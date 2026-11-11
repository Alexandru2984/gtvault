// Starea globala a aplicatiei: sesiune, sold, cos, wishlist, notificari. Evenimente simple pub/sub.
import { get, configureApi } from './api.js';

const TOKEN_KEY = 'gt.session';
const listeners = new Map();

export const state = {
  token: null,
  expiresAt: 0,
  user: null,
  tier: null,
  cartCount: 0,
  wishlist: new Set(),
  unread: 0,
  badgeCatalog: null,
};

export function on(event, fn) {
  if (!listeners.has(event)) listeners.set(event, new Set());
  listeners.get(event).add(fn);
  return () => listeners.get(event)?.delete(fn);
}

export function emit(event, payload) {
  for (const fn of listeners.get(event) || []) {
    try { fn(payload); } catch (e) { console.error(e); }
  }
}

function storage() {
  try { return window.localStorage; } catch { return null; }
}

// ------------------------------------------------------------------ sesiune

export function loadSession() {
  try {
    const raw = storage()?.getItem(TOKEN_KEY);
    if (!raw) return false;
    const s = JSON.parse(raw);
    if (s && typeof s.token === 'string' && s.expiresAt > Date.now()) {
      if (state.user?.id !== s.user?.id) clearAccountData();
      state.token = s.token;
      state.expiresAt = s.expiresAt;
      state.user = s.user || null;
      return true;
    }
    storage()?.removeItem(TOKEN_KEY);
  } catch { /* stocarea poate fi indisponibila */ }
  return false;
}

function persist() {
  try {
    if (state.token) storage()?.setItem(TOKEN_KEY, JSON.stringify({ token: state.token, expiresAt: state.expiresAt, user: state.user }));
    else storage()?.removeItem(TOKEN_KEY);
  } catch { /* stocare plina sau blocata: sesiunea ramane doar in memorie */ }
}

function clearAccountData() {
  state.tier = null;
  state.cartCount = 0;
  state.wishlist = new Set();
  state.unread = 0;
  state.badgeCatalog = null;
}

/** Raspunsul de la /auth/login, /auth/register, /auth/password. */
export function startSession(auth) {
  if (state.user?.id !== auth.user?.id) clearAccountData();
  state.token = auth.token;
  state.expiresAt = Date.parse(auth.expiresAt) || Date.now() + 6 * 86400_000;
  state.user = auth.user;
  persist();
  emit('session', state.user);
  emit('user', state.user);
}

export function endSession(reason) {
  const had = !!state.token;
  state.token = null;
  state.expiresAt = 0;
  state.user = null;
  clearAccountData();
  persist();
  if (had) emit('session', null, reason);
  emit('user', null);
  emit('cart', 0);
  emit('wishlist', state.wishlist);
  emit('unread', 0);
  if (had && reason) emit('expired', reason);
}

export const isAuthed = () => !!state.token && state.expiresAt > Date.now();
export const isAdmin = () => isAuthed() && state.user?.role === 'ADMIN';

configureApi({
  getToken: () => (isAuthed() ? state.token : null),
  unauthorized: () => endSession('expired'),
});

// Sincronizare intre tab-uri: login/logout intr-un tab se reflecta in celelalte
window.addEventListener('storage', (e) => {
  if (e.key !== TOKEN_KEY) return;
  const before = state.token;
  if (!loadSession()) {
    if (before) {
      endSession();
      emit('account-change');
    }
    return;
  }
  if (state.token !== before) {
    emit('session', state.user);
    emit('user', state.user);
    emit('account-change');
    refreshAll();
  }
});

// ------------------------------------------------------------------ profil si sold

export function setUser(user) {
  if (!user) return;
  state.user = { ...state.user, ...user };
  persist();
  emit('user', state.user);
}

export function setBalance(n) {
  if (!state.user || typeof n !== 'number') return;
  const changed = state.user.activityPoints !== n;
  state.user = { ...state.user, activityPoints: n };
  persist();
  emit('user', state.user);
  if (changed) emit('balance', n);
}

export async function refreshUser() {
  if (!isAuthed()) return null;
  const token = state.token;
  try {
    const [me, stats] = await Promise.all([get('/user/me'), get('/user/stats')]);
    if (state.token !== token) return null;
    state.tier = stats?.tier || null;
    setUser(me);
    emit('tier', state.tier);
    return me;
  } catch {
    return null;
  }
}

export async function refreshCart() {
  if (!isAuthed()) return;
  const token = state.token;
  try {
    const cart = await get('/cart');
    if (state.token === token) setCartCount(cart?.itemCount || 0);
  } catch { /* indicatorul cosului nu e critic */ }
}

export function setCartCount(n) {
  state.cartCount = n || 0;
  emit('cart', state.cartCount);
}

export async function refreshWishlist() {
  if (!isAuthed()) return;
  const token = state.token;
  try {
    const ids = await get('/wishlist/ids');
    if (state.token !== token) return;
    state.wishlist = new Set(Array.isArray(ids) ? ids : []);
    emit('wishlist', state.wishlist);
  } catch { /* ignorat */ }
}

export async function refreshUnread() {
  if (!isAuthed()) return;
  const token = state.token;
  try {
    const r = await get('/notifications/unread-count');
    if (state.token === token) setUnread(r?.unread || 0);
  } catch { /* ignorat */ }
}

export function setUnread(n) {
  state.unread = Math.max(0, n || 0);
  emit('unread', state.unread);
}

export function refreshAll() {
  if (!isAuthed()) return Promise.resolve();
  return Promise.all([refreshUser(), refreshCart(), refreshWishlist(), refreshUnread()]);
}

/** Catalogul de insigne (titluri, iconite) pentru a afisa frumos insignele deblocate. */
export async function badgeCatalog() {
  if (state.badgeCatalog) return state.badgeCatalog;
  if (!isAuthed()) return new Map();
  const token = state.token;
  try {
    const r = await get('/gamification/badges');
    const badges = new Map((r?.badges || []).map((b) => [b.code, b]));
    if (state.token === token) state.badgeCatalog = badges;
    return badges;
  } catch {
    return new Map();
  }
}

// ------------------------------------------------------------------ cod de recomandare din link

const REF_KEY = 'gt.ref';

export function captureReferral() {
  const params = new URLSearchParams(location.search);
  const ref = (params.get('ref') || '').trim().toUpperCase();
  if (/^GT-[A-Z0-9]{4,12}$/.test(ref)) {
    try { storage()?.setItem(REF_KEY, JSON.stringify({ code: ref, at: Date.now() })); } catch { /* ignorat */ }
    params.delete('ref');
    const qs = params.toString();
    history.replaceState(history.state, '', location.pathname + (qs ? `?${qs}` : '') + location.hash);
  }
}

export function pendingReferral() {
  try {
    const r = JSON.parse(storage()?.getItem(REF_KEY) || 'null');
    if (r && Date.now() - r.at < 30 * 86400_000) return r.code;
  } catch { /* ignorat */ }
  return '';
}

export function clearReferral() {
  try { storage()?.removeItem(REF_KEY); } catch { /* ignorat */ }
}

// ------------------------------------------------------------------ preferinte

export function pref(key, fallback = null) {
  try {
    const v = storage()?.getItem(`gt.pref.${key}`);
    return v == null ? fallback : JSON.parse(v);
  } catch { return fallback; }
}

export function setPref(key, value) {
  try { storage()?.setItem(`gt.pref.${key}`, JSON.stringify(value)); } catch { /* ignorat */ }
}
