// Actiuni folosite din mai multe pagini: cos, favorite, deconectare.
import { qsa } from './dom.js';
import { post, put, del, enc } from './api.js';
import { state, isAuthed, endSession, setCartCount, refreshUnread, emit } from './store.js';
import { navigate } from './router.js';
import { toast, toastOk, handleError } from './ui.js';

function requireLogin(message) {
  toast(message, { icon: '🔐' });
  navigate(`/autentificare?next=${enc(location.pathname + location.search)}`);
}

export async function addToCart(btn, rewardId, quantity = 1) {
  if (!isAuthed()) {
    requireLogin('Intră în cont ca să adaugi premii în coș.');
    return null;
  }
  if (btn?.getAttribute('aria-busy') === 'true') return null;
  const token = state.token;
  btn?.setAttribute('aria-busy', 'true');
  try {
    const cart = await post('/cart/items', { rewardId, quantity });
    if (state.token !== token) return null;
    setCartCount(cart.itemCount);
    const line = (cart.lines || []).find((l) => l.rewardId === rewardId);
    toastOk(line ? `„${line.name}” e în coș (${line.quantity} buc.)` : 'Adăugat în coș.', {
      icon: '🛒', action: { label: 'Vezi coșul', href: '/cos' },
    });
    emit('cart:changed', cart);
    return cart;
  } catch (err) {
    handleError(err);
    return null;
  } finally {
    btn?.removeAttribute('aria-busy');
  }
}

export async function toggleWish(btn, rewardId) {
  if (!isAuthed()) {
    requireLogin('Intră în cont ca să salvezi premii la favorite.');
    return;
  }
  if (btn?.dataset.pending) return;
  const token = state.token;
  if (btn) btn.dataset.pending = '1';
  const wasOn = state.wishlist.has(rewardId);
  try {
    const r = wasOn ? await del(`/wishlist/${enc(rewardId)}`) : await put(`/wishlist/${enc(rewardId)}`);
    if (state.token !== token) return;
    if (r.wishlisted) state.wishlist.add(rewardId);
    else state.wishlist.delete(rewardId);
    for (const b of qsa(`[data-wish="${CSS.escape(rewardId)}"]`)) {
      b.setAttribute('aria-pressed', String(!!r.wishlisted));
      b.classList.remove('is-pop');
      void b.offsetWidth; // reporneste animatia
      if (r.wishlisted) b.classList.add('is-pop');
    }
    emit('wishlist', state.wishlist);
    emit('wishlist:toggled', { rewardId, wishlisted: !!r.wishlisted });
    toast(r.wishlisted ? 'Salvat la favorite. Te anunțăm când intră la reducere.' : 'Scos din favorite.', {
      icon: r.wishlisted ? '❤️' : '🤍', timeout: 2600,
    });
  } catch (err) {
    handleError(err);
  } finally {
    if (btn) delete btn.dataset.pending;
  }
}

export async function setSavingGoal(btn, rewardId) {
  if (!isAuthed()) {
    requireLogin('Intră în cont ca să salvezi un obiectiv.');
    return;
  }
  if (btn?.getAttribute('aria-busy') === 'true') return;
  const token = state.token;
  btn?.setAttribute('aria-busy', 'true');
  try {
    await put('/user/saving-goal', { rewardId });
    if (state.token !== token) return;
    emit('goal:changed');
    refreshUnread();
    toastOk('Premiul a devenit obiectivul tău!', { icon: '🎯', action: { label: 'Vezi progresul', href: '/favorite' } });
  } catch (err) { if (state.token === token) handleError(err); }
  finally { btn?.removeAttribute('aria-busy'); }
}

let logoutPending = false;

export async function logout() {
  if (logoutPending) return;
  logoutPending = true;
  const token = state.token;
  let revoked = false;
  try {
    if (token) await post('/auth/logout');
    revoked = true;
  } catch (err) {
    // Un 401 înseamnă că tokenul nu mai oferă deja acces.
    revoked = err.status === 401;
  } finally {
    logoutPending = false;
  }
  if (state.token && state.token !== token) return;
  endSession();
  toast(revoked ? 'Te-ai deconectat. Pe curând! 👋' : 'Ai ieșit de pe acest dispozitiv. Revocarea pe server nu a fost confirmată.', { timeout: revoked ? 3500 : 7000 });
  navigate('/');
}
