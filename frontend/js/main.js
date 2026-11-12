// Punctul de intrare: sesiune, antet, rute, handler-e globale (cos, favorite), notificari, PWA.
import { html, mount, on, qs, qsa, safeHref, join } from './dom.js';
import { icon, coin } from './icons.js';
import { post, get, enc, isAbort } from './api.js';
import { addToCart, toggleWish, setSavingGoal, logout } from './actions.js';
import {
  state, on as onStore, loadSession, isAuthed, isAdmin, refreshAll, refreshUser, refreshUnread,
  captureReferral, setUnread,
} from './store.js';
import { route, startRouter, configureRouter, navigate, currentRoute, reload, returnPath } from './router.js';
import { toast, handleError } from './ui.js';
import { num, countdown, timeAgo } from './format.js';
import { avatar, emptyState } from './components.js';
import { setMeta } from './seo.js';
import { toggleTheme, currentTheme } from './theme-toggle.js';
import { toggleCompared, compareLink } from './compare.js';

// ------------------------------------------------------------------ rute

const v = (name) => () => import(`./views/${name}.js`);

route('/', v('home'));
route('/magazin', v('shop'));
route('/compara', v('compare'));
route('/vouchere', v('vouchers'));
route('/produs/:id', v('product'));
route('/cos', v('cart'), { auth: true });
route('/finalizare', v('checkout'), { auth: true });
route('/comenzi', v('orders'), { auth: true });
route('/comenzi/:id', v('order'), { auth: true });
route('/favorite', v('wishlist'), { auth: true });
route('/notificari', v('notifications'), { auth: true });
route('/autentificare', v('auth'), { guest: true });
route('/inregistrare', v('auth'), { guest: true });
route('/activitati', v('activities'));
route('/wordle', v('wordle'));
route('/clasament', v('leaderboard'));
route('/bresle', v('guilds'));
route('/comunitate', v('community'));
route('/profil', v('profile'), { auth: true });
route('/istoric', v('history'), { auth: true });
route('/membru/:id', v('member'));
route('/admin', v('admin/index'), { auth: true, admin: true });
route('/admin/:section', v('admin/index'), { auth: true, admin: true });
route('/ajutor', v('help'));
route('/confidentialitate', v('legal'));
route('/termeni', v('legal'));
route('/404', v('notfound'));

const NAV_MATCH = {
  '/magazin': ['/magazin', '/produs/'],
  '/activitati': ['/activitati'],
  '/wordle': ['/wordle'],
  '/comunitate': ['/comunitate', '/membru/'],
  '/clasament': ['/clasament', '/bresle'],
  '/': ['/'],
  '/profil': ['/profil', '/istoric', '/comenzi', '/favorite', '/notificari', '/autentificare', '/inregistrare'],
};

function updateNavCurrent(path) {
  for (const a of qsa('.mainnav a, .tabbar a')) {
    const key = a.getAttribute('href');
    const prefixes = NAV_MATCH[key] || [key];
    const active = prefixes.some((p) => (p === '/' ? path === '/' : path === p || path.startsWith(p.endsWith('/') ? p : `${p}/`) || path === p));
    if (active) a.setAttribute('aria-current', 'page');
    else a.removeAttribute('aria-current');
  }
}

configureRouter({
  guard(meta, target) {
    if (meta.auth && !isAuthed()) return `/autentificare?next=${enc(target)}`;
    if (meta.admin && !isAdmin()) return '/profil';
    if (meta.guest && isAuthed()) {
      const next = new URLSearchParams(location.search).get('next');
      return returnPath(next);
    }
    return null;
  },
  beforeRender() {
    for (const p of qsa('[popover]')) {
      try { if (p.matches(':popover-open')) p.hidePopover(); } catch { /* fara suport popover */ }
    }
  },
  afterRender(r, path) {
    updateNavCurrent(path);
    const h1 = qs('#main h1');
    if (h1 && document.activeElement !== qs('#search-top')) {
      h1.setAttribute('tabindex', '-1');
      h1.focus({ preventScroll: true });
    }
    tickCountdowns();
  },
  onError(err, root) {
    setMeta({ title: 'Eroare', noindex: true });
    mount(root, html`<div class="container">${emptyState({
      icon: '😵‍💫',
      title: err?.status === 404 ? 'Nu am găsit ce cauți' : 'Pagina nu a putut fi încărcată',
      text: err?.message || 'A apărut o problemă neașteptată.',
      action: html`<button class="btn btn-primary" type="button" data-reload>${icon('refresh')} Reîncearcă</button>`,
    })}</div>`);
    qs('[data-reload]', root)?.addEventListener('click', () => reload());
  },
});

// ------------------------------------------------------------------ antet

const POPOVER = typeof HTMLElement !== 'undefined' && Object.prototype.hasOwnProperty.call(HTMLElement.prototype, 'popover');

function renderHeader() {
  const box = qs('#top-actions');
  if (!box) return;
  const u = state.user;
  const search = html`<form class="searchbox" role="search" action="/magazin" data-search>
      ${icon('search')}<label class="sr-only" for="search-top">Caută premii</label>
      <input id="search-top" name="q" type="search" placeholder="Caută premii…" autocomplete="off" enterkeyhint="search">
    </form>`;
  if (!isAuthed() || !u) {
    for (const menu of qsa('#user-menu, #notif-menu')) {
      if (menu.matches(':popover-open')) menu.hidePopover();
      menu.replaceChildren();
    }
    mount(box, html`
      ${search}
      <button class="icon-btn" type="button" data-theme-toggle aria-label="Schimbă tema">${icon(currentTheme() === 'dark' ? 'sun' : 'moon')}</button>
      <a class="btn btn-ghost btn-sm" href="/autentificare">Intră</a>
      <a class="btn btn-primary btn-sm hide-mobile" href="/inregistrare">Creează cont</a>`);
    return;
  }
  const bell = POPOVER
    ? html`<button class="icon-btn" type="button" popovertarget="notif-menu" aria-label="Notificări${state.unread ? `, ${state.unread} necitite` : ''}">${icon('bell')}${state.unread ? html`<span class="count">${state.unread > 99 ? '99+' : state.unread}</span>` : ''}</button>`
    : html`<a class="icon-btn" href="/notificari" aria-label="Notificări">${icon('bell')}${state.unread ? html`<span class="count">${state.unread > 99 ? '99+' : state.unread}</span>` : ''}</a>`;
  const userBtn = POPOVER
    ? html`<button class="user-btn" type="button" popovertarget="user-menu" aria-label="Meniul contului">${avatar(u.avatar, u.name, 'sm')}</button>`
    : html`<a class="user-btn" href="/profil" aria-label="Profilul meu">${avatar(u.avatar, u.name, 'sm')}</a>`;
  mount(box, html`
    ${search}
    <a class="ap-pill" href="/istoric" id="ap-pill" aria-label="Sold: ${num(u.activityPoints)} AP">${coin()}<span>${num(u.activityPoints)}</span></a>
    ${bell}
    <a class="icon-btn" href="/cos" aria-label="Coș${state.cartCount ? `, ${state.cartCount} produse` : ''}">${icon('cart')}${state.cartCount ? html`<span class="count">${state.cartCount}</span>` : ''}</a>
    ${userBtn}`);
  renderUserMenu();
}

function renderUserMenu() {
  const menu = qs('#user-menu');
  const u = state.user;
  if (!menu || !u) return;
  mount(menu, html`
    <div class="menu-head">${avatar(u.avatar, u.name)}<div class="grow"><div class="strong ellipsis">${u.name}</div><div class="xsmall muted ellipsis">${u.email}</div></div></div>
    <div class="menu-sep"></div>
    <a href="/profil">${icon('user')} Profilul meu</a>
    <a href="/comenzi">${icon('package')} Comenzile mele</a>
    <a href="/favorite">${icon('heart')} Favorite</a>
    <a href="/istoric">${icon('history')} Istoric puncte</a>
    <a href="/profil?tab=invita">${icon('gift')} Invită prieteni</a>
    ${isAdmin() ? html`<div class="menu-sep"></div><a href="/admin">${icon('shield')} Administrare</a>` : ''}
    <div class="menu-sep"></div>
    <button class="menu-item" type="button" data-theme-toggle>${icon(currentTheme() === 'dark' ? 'sun' : 'moon')} Temă ${currentTheme() === 'dark' ? 'luminoasă' : 'întunecată'}</button>
    ${installPrompt ? html`<button class="menu-item" type="button" data-install>${icon('phone')} Instalează aplicația</button>` : ''}
    <a href="/profil?tab=setari">${icon('sliders')} Setări</a>
    <button class="menu-item is-danger" type="button" data-logout>${icon('logout')} Ieși din cont</button>`);
}

async function renderNotifMenu() {
  const menu = qs('#notif-menu');
  if (!menu || !isAuthed()) return;
  const token = state.token;
  mount(menu, html`<div class="menu-title"><strong>Notificări</strong></div><div class="card-pad"><div class="skel skel-line"></div><div class="skel skel-line mt-1"></div></div>`);
  try {
    const r = await get('/notifications?limit=8');
    if (state.token !== token) return;
    setUnread(r.unread);
    const items = r.items || [];
    mount(menu, html`
      <div class="menu-title"><strong>Notificări</strong>${r.unread ? html`<button class="link-btn small" type="button" data-read-all>Marchează tot ca citit</button>` : ''}</div>
      ${items.length
        ? join(items, (n) => html`<div class="notif ${n.read ? '' : 'is-unread'}">
            <span class="notif-ico" aria-hidden="true">${n.icon || '🔔'}</span>
            <a class="notif-main" href="${n.link ? safeHref(n.link) : '/notificari'}" data-notif="${n.id}" data-unread="${!n.read}">
              <div class="notif-title">${n.title}</div>
              ${n.body ? html`<div class="notif-body">${n.body}</div>` : ''}
              <div class="notif-at">${timeAgo(n.createdAt)}</div>
            </a><span></span>
          </div>`)
        : html`<div class="card-pad center muted">Nu ai notificări încă. Joacă, comandă și primești vești aici. 🔔</div>`}
      <div class="menu-foot"><a href="/notificari">${icon('bell')} Toate notificările</a></div>`);
  } catch (err) {
    if (state.token === token && !isAbort(err)) mount(menu, html`<div class="card-pad muted">Nu am putut încărca notificările.</div>`);
  }
}

// ------------------------------------------------------------------ handler-e globale

let installPrompt = null;

function bindGlobal() {
  on(document, 'click', '[data-compare]', (event, button) => {
    event.preventDefault();
    const ids = toggleCompared(button.dataset.compare);
    if (!ids) {
      toast('Poți compara până la 3 premii. Scoate unul pentru a adăuga altul.', { action: { label: 'Vezi comparația', href: compareLink() } });
      return;
    }
    const added = ids.includes(button.dataset.compare);
    toast(added ? 'Premiu adăugat la comparație.' : 'Premiu scos din comparație.', {
      action: ids.length ? { label: `Compară ${ids.length} premii`, href: compareLink(ids) } : null,
    });
  });
  on(document, 'click', '[data-add-cart]', (e, btn) => {
    e.preventDefault();
    addToCart(btn, btn.dataset.addCart, Number(btn.dataset.qty || 1));
  });
  on(document, 'click', '[data-wish]', (e, btn) => {
    e.preventDefault();
    toggleWish(btn, btn.dataset.wish);
  });
  on(document, 'click', '[data-logout]', (e) => { e.preventDefault(); logout(); });
  on(document, 'click', '[data-goal-set]', (e, btn) => {
    e.preventDefault();
    setSavingGoal(btn, btn.dataset.goalSet);
  });
  on(document, 'click', '[data-theme-toggle]', () => {
    toggleTheme();
    renderHeader();
  });
  on(document, 'click', '[data-install]', async () => {
    if (!installPrompt) return;
    installPrompt.prompt();
    await installPrompt.userChoice.catch(() => null);
    installPrompt = null;
    renderUserMenu();
  });
  on(document, 'click', '[data-read-all]', async (e, btn) => {
    btn.disabled = true;
    try {
      await post('/notifications/read-all');
      setUnread(0);
      renderNotifMenu();
    } catch (err) { handleError(err); btn.disabled = false; }
  });
  on(document, 'click', '[data-notif]', (e, a) => {
    if (a.dataset.unread === 'true') {
      post(`/notifications/${enc(a.dataset.notif)}/read`).then(() => refreshUnread()).catch(() => {});
    }
  });
  on(document, 'submit', 'form[data-search]', (e, form) => {
    e.preventDefault();
    const q = new FormData(form).get('q')?.toString().trim() || '';
    form.querySelector('input').blur();
    navigate(`/magazin${q ? `?q=${enc(q)}` : ''}`);
  });
  document.addEventListener('keydown', (e) => {
    if (e.key === '/' && !e.ctrlKey && !e.metaKey && !/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName) && !document.activeElement?.isContentEditable) {
      const input = qs('#search-top') && getComputedStyle(qs('.searchbox')).display !== 'none' ? qs('#search-top') : qs('#shop-q');
      if (input) { e.preventDefault(); input.focus(); }
    }
  });
  const notifMenu = qs('#notif-menu');
  notifMenu?.addEventListener('toggle', (e) => { if (e.newState === 'open') renderNotifMenu(); });
  qs('#user-menu')?.addEventListener('toggle', (e) => { if (e.newState === 'open') renderUserMenu(); });

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    installPrompt = e;
  });
  window.addEventListener('online', () => qs('#offline-bar')?.setAttribute('hidden', ''));
  window.addEventListener('offline', () => qs('#offline-bar')?.removeAttribute('hidden'));
}

// ------------------------------------------------------------------ cronometre

function tickCountdowns() {
  const now = Date.now();
  for (const el of qsa('[data-countdown]')) {
    const end = Date.parse(el.dataset.countdown);
    if (Number.isNaN(end)) continue;
    const left = end - now;
    const text = el.querySelector('.cd-text') || el;
    if (left <= 0) {
      text.textContent = el.dataset.expired || 'Expirat';
      el.dispatchEvent(new CustomEvent('countdown:end', { bubbles: true }));
      el.removeAttribute('data-countdown');
    } else {
      text.textContent = (el.dataset.prefix ? `${el.dataset.prefix} ` : '') + countdown(left);
    }
  }
}

// ------------------------------------------------------------------ pornire

function bindStore() {
  onStore('compare', (ids) => {
    for (const button of qsa('[data-compare]')) button.setAttribute('aria-pressed', String(ids.includes(button.dataset.compare)));
    for (const link of qsa('[data-compare-link]')) {
      link.href = compareLink(ids);
      link.textContent = `Compară (${ids.length})`;
    }
  });
  onStore('user', renderHeader);
  onStore('cart', renderHeader);
  onStore('unread', renderHeader);
  onStore('account-change', reload);
  onStore('balance', () => {
    const pill = qs('#ap-pill');
    if (!pill) return;
    pill.classList.remove('is-bump');
    void pill.offsetWidth;
    pill.classList.add('is-bump');
  });
  onStore('expired', () => {
    toast('Sesiunea a expirat. Intră din nou în cont.', { icon: '🔐', type: 'error' });
    const meta = currentRoute()?.route?.meta;
    if (meta?.auth) navigate(`/autentificare?next=${enc(location.pathname + location.search)}`, { replace: true });
  });
}

let lastRefresh = Date.now();

function startPolling() {
  setInterval(() => {
    if (document.visibilityState === 'visible' && isAuthed()) refreshUnread();
  }, 60_000);
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState !== 'visible' || !isAuthed()) return;
    refreshUnread();
    if (Date.now() - lastRefresh > 60_000) {
      lastRefresh = Date.now();
      refreshUser();
    }
  });
  setInterval(tickCountdowns, 1000);
}

function registerServiceWorker() {
  if (!('serviceWorker' in navigator) || (location.hostname === 'localhost' && !location.search.includes('sw=1'))) return;
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(() => {});
  });
}

function init() {
  captureReferral();
  loadSession();
  renderHeader();
  bindGlobal();
  bindStore();
  startPolling();
  if (!navigator.onLine) qs('#offline-bar')?.removeAttribute('hidden');
  startRouter();
  if (isAuthed()) refreshAll();
  registerServiceWorker();
  const year = qs('#year');
  if (year) year.textContent = String(new Date().getFullYear());
}

init();
