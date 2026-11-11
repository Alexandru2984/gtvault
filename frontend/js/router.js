import { internalPath } from './dom.js';

// Router pe History API cu cai curate (/produs/rew-tricou), incarcare lenesa a view-urilor
// si anularea randarilor depasite (AbortController per navigare).

const routes = [];
let current = null;
let navId = 0;
let renderedKey = null;
let hooks = { beforeRender: null, afterRender: null, guard: null };

export function route(pattern, load, meta = {}) {
  const keys = [];
  const rx = new RegExp(
    '^' + pattern.replace(/\/+$/, '').replace(/:([a-zA-Z]+)/g, (_, k) => { keys.push(k); return '([^/]+)'; }) + '/?$',
  );
  routes.push({ pattern, rx, keys, load, meta });
}

export function match(pathname) {
  for (const r of routes) {
    const m = pathname.match(r.rx);
    if (m) {
      const params = {};
      r.keys.forEach((k, i) => {
        try { params[k] = decodeURIComponent(m[i + 1]); } catch { params[k] = m[i + 1]; }
      });
      return { route: r, params };
    }
  }
  return null;
}

export function configureRouter(h) {
  hooks = { ...hooks, ...h };
}

export const currentRoute = () => current;

export function returnPath(value) {
  const path = internalPath(value);
  const destination = path && match(new URL(path, location.origin).pathname);
  return destination && !destination.route.meta.guest ? path : '/';
}

function saveScroll() {
  try {
    history.replaceState({ ...(history.state || {}), scroll: window.scrollY }, '');
  } catch { /* ignorat */ }
}

/** Navigheaza intern. `to` poate contine query string. */
export function navigate(to, { replace = false, scroll = true } = {}) {
  const target = internalPath(to);
  if (!target) return;
  if (!replace) saveScroll();
  if (replace) history.replaceState({ scroll: 0 }, '', target);
  else history.pushState({ scroll: 0 }, '', target);
  render({ restoreScroll: false, scrollTop: scroll });
}

/** Re-randeaza pagina curenta (ex: dupa login/logout). */
export const reload = () => render({ restoreScroll: false, scrollTop: false });

/** Actualizeaza query string-ul fara a re-randa (filtre, cautare). */
export function setQuery(params, { push = false } = {}) {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '' && v !== false) sp.set(k, String(v));
  }
  const s = sp.toString();
  const url = location.pathname + (s ? `?${s}` : '');
  if (push) history.pushState({ scroll: 0 }, '', url);
  else history.replaceState(history.state, '', url);
  renderedKey = location.pathname + location.search;
}

async function render({ restoreScroll, scrollTop }) {
  const id = ++navId;
  const root = document.getElementById('main');
  const pathname = location.pathname;
  renderedKey = pathname + location.search;
  const found = match(pathname) || match('/404');
  const query = Object.fromEntries(new URLSearchParams(location.search));

  if (current?.controller) current.controller.abort();
  if (typeof current?.cleanup === 'function') {
    try { current.cleanup(); } catch (e) { console.error(e); }
  }
  const controller = new AbortController();
  current = { ...found, path: pathname, query, controller, cleanup: null };

  const redirect = hooks.guard?.(found.route.meta, pathname + location.search);
  if (redirect) {
    navigate(redirect, { replace: true });
    return;
  }

  hooks.beforeRender?.(found.route, pathname);

  let mod;
  try {
    mod = await found.route.load();
  } catch (err) {
    console.error(err);
    if (id !== navId) return;
    root.innerHTML = '';
    const p = document.createElement('div');
    p.className = 'empty';
    p.innerHTML = '<div class="empty-icon">🔌</div><h3>Pagina nu s-a putut încărca</h3><p>Verifică conexiunea și reîncarcă pagina.</p>';
    root.append(p);
    return;
  }
  if (id !== navId) return;

  const ctx = {
    root,
    params: found.params,
    query,
    signal: controller.signal,
    alive: () => id === navId && !controller.signal.aborted,
  };

  try {
    const cleanup = await mod.default(ctx);
    if (id === navId) current.cleanup = cleanup;
    else if (typeof cleanup === 'function') cleanup();
  } catch (err) {
    if (err?.name === 'AbortError' || id !== navId) return;
    console.error(err);
    hooks.onError?.(err, root);
  }
  if (id !== navId) return;

  if (restoreScroll) {
    const y = history.state?.scroll || 0;
    requestAnimationFrame(() => window.scrollTo(0, y));
  } else if (scrollTop) {
    window.scrollTo(0, 0);
  }
  hooks.afterRender?.(found.route, pathname);
}

function isInternalLink(a, e) {
  if (!a || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return false;
  if (a.target && a.target !== '_self') return false;
  if (a.hasAttribute('download') || a.dataset.external != null) return false;
  const href = a.getAttribute('href');
  if (!href || href === '#' || href.startsWith('mailto:') || href.startsWith('tel:')) return false;
  if (href.startsWith('#/')) return true;
  if (href.startsWith('#')) return false;
  const url = new URL(a.href, location.href);
  return url.origin === location.origin && !url.pathname.startsWith('/RewardHub/');
}

export function startRouter() {
  history.scrollRestoration = 'manual';
  // Link-uri vechi cu hash (#/comenzi/ORD-1, folosite si in notificari) -> cai curate
  const legacyPath = location.hash.startsWith('#/') && internalPath(location.hash.slice(1));
  if (legacyPath) history.replaceState(null, '', legacyPath);
  document.addEventListener('click', (e) => {
    const a = e.target instanceof Element ? e.target.closest('a[href]') : null;
    if (!isInternalLink(a, e)) return;
    e.preventDefault();
    const href = a.getAttribute('href');
    const to = href.startsWith('#/') ? href.slice(1) : new URL(a.href).pathname + new URL(a.href).search + new URL(a.href).hash;
    if (to === location.pathname + location.search && !a.dataset.force) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }
    navigate(to);
  });
  window.addEventListener('popstate', () => {
    // Ancorele din pagina (#recenzii) schimba doar fragmentul: nu re-randam
    if (location.pathname + location.search === renderedKey) return;
    render({ restoreScroll: true });
  });
  window.addEventListener('pagehide', saveScroll);
  render({ restoreScroll: true });
}
