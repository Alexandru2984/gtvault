// Componente de interfata imperative: toast-uri, dialoguri, confetti, copiere, partajare.
import { html, mount, fragment, qs, esc } from './dom.js';
import { icon } from './icons.js';
import { isAbort } from './api.js';
import { state, setBalance, badgeCatalog, emit } from './store.js';
import { ap } from './format.js';

const reducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// ------------------------------------------------------------------ toast

export function toast(message, { type = 'info', title = null, icon: ico = null, timeout = 4200, action = null } = {}) {
  const region = document.getElementById('toasts');
  if (!region) return;
  const defaultIcon = { success: '✅', error: '⚠️', reward: '🎉', info: 'ℹ️' }[type] || 'ℹ️';
  const el = fragment(html`
    <div class="toast toast-${type}" role="${type === 'error' ? 'alert' : 'status'}">
      <span class="toast-icon" aria-hidden="true">${ico || defaultIcon}</span>
      <div class="toast-body">
        ${title ? html`<div class="toast-title">${title}</div><div class="toast-text">${message}</div>` : html`<div>${message}</div>`}
        ${action ? html`<a class="toast-action" href="${action.href}">${action.label} →</a>` : ''}
      </div>
      <button class="toast-close" type="button" aria-label="Închide notificarea">${icon('x')}</button>
    </div>`);
  const close = () => {
    if (!el.isConnected) return;
    el.classList.add('is-leaving');
    setTimeout(() => el.remove(), 260);
  };
  el.querySelector('.toast-close').addEventListener('click', close);
  el.querySelector('.toast-action')?.addEventListener('click', close);
  region.append(el);
  while (region.children.length > 4) region.firstElementChild.remove();
  if (timeout) setTimeout(close, timeout);
  return close;
}

export const toastOk = (msg, opts) => toast(msg, { type: 'success', ...opts });
export const toastError = (msg, opts) => toast(msg, { type: 'error', timeout: 6000, ...opts });

/** Afiseaza o eroare de API intr-un mod prietenos; ignora anularile. */
export function handleError(err, fallback = 'Ceva nu a mers bine. Încearcă din nou.') {
  if (isAbort(err)) return;
  if (err?.status === 401) return; // sesiunea expirata e tratata global
  console.warn(err);
  toastError(err?.message || fallback);
}

// ------------------------------------------------------------------ actiuni cu stare de incarcare

/** Ruleaza o actiune asincrona cu butonul in stare "busy"; previne dublu-click. */
export async function busy(btn, fn) {
  if (btn?.getAttribute('aria-busy') === 'true') return undefined;
  btn?.setAttribute('aria-busy', 'true');
  if (btn) btn.disabled = true;
  try {
    return await fn();
  } finally {
    if (btn?.isConnected) {
      btn.removeAttribute('aria-busy');
      btn.disabled = false;
    }
  }
}

/** Dupa o actiune care castiga puncte/insigne: actualizeaza soldul si sarbatoreste. */
export async function celebrate(result, { confettiOn = false } = {}) {
  if (!result) return;
  if (typeof result.newBalance === 'number') setBalance(result.newBalance);
  const codes = Array.isArray(result.badgesUnlocked) ? result.badgesUnlocked : [];
  if (codes.length) {
    const catalog = await badgeCatalog();
    state.badgeCatalog = null; // insignele s-au schimbat; reincarcam data viitoare
    for (const code of codes) {
      const b = catalog.get(code);
      toast(b ? `${b.description}${b.rewardPoints ? ` · +${ap(b.rewardPoints)}` : ''}` : 'Ai deblocat o insignă nouă!', {
        type: 'reward', title: b ? `Insignă nouă: ${b.title}` : 'Insignă nouă!', icon: b?.icon || '🏅', timeout: 7000,
      });
    }
    emit('badges', codes);
  }
  if (confettiOn || codes.length) confetti();
}

// ------------------------------------------------------------------ modal

let openCount = 0;

/**
 * Deschide un dialog modal. `content` este un sablon html``; `onMount(body, close)` leaga evenimentele.
 * Returneaza o promisiune rezolvata cu valoarea transmisa lui close(value).
 */
export function modal({ title, content, footer = null, size = '', onMount = null, dismissible = true }) {
  return new Promise((resolve) => {
    const dlg = document.createElement('dialog');
    dlg.className = `modal ${size === 'lg' ? 'modal-lg' : ''}`;
    dlg.setAttribute('aria-labelledby', 'modal-title-' + (++openCount));
    mount(dlg, html`
      <div class="modal-head">
        <h2 id="modal-title-${openCount}">${title}</h2>
        ${dismissible ? html`<button class="icon-btn" type="button" data-close aria-label="Închide">${icon('x')}</button>` : ''}
      </div>
      <div class="modal-body">${content}</div>
      ${footer ? html`<div class="modal-foot">${footer}</div>` : ''}`);
    let result;
    let done = false;
    const close = (value) => {
      if (done) return;
      done = true;
      result = value;
      dlg.close();
    };
    dlg.addEventListener('close', () => {
      dlg.remove();
      resolve(result);
    });
    dlg.addEventListener('cancel', (e) => { if (!dismissible) e.preventDefault(); });
    dlg.addEventListener('click', (e) => {
      if (e.target.closest('[data-close]')) close(undefined);
      // click pe fundal (in afara chenarului)
      if (dismissible && e.target === dlg) {
        const r = dlg.getBoundingClientRect();
        const inside = e.clientX >= r.left && e.clientX <= r.right && e.clientY >= r.top && e.clientY <= r.bottom;
        if (!inside) close(undefined);
      }
    });
    document.body.append(dlg);
    dlg.showModal();
    onMount?.(dlg, close);
    const auto = dlg.querySelector('[autofocus]') || dlg.querySelector('.modal-body input, .modal-body select, .modal-body textarea');
    if (auto) auto.focus();
  });
}

export function confirmDialog({ title, body, confirmText = 'Confirmă', cancelText = 'Renunță', danger = false }) {
  return modal({
    title,
    content: html`<p class="muted">${body}</p>`,
    footer: html`
      <button class="btn btn-ghost" type="button" data-close>${cancelText}</button>
      <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="button" data-ok>${confirmText}</button>`,
    onMount: (dlg, close) => {
      const ok = qs('[data-ok]', dlg);
      ok.addEventListener('click', () => close(true));
      ok.focus();
    },
  }).then((v) => v === true);
}

/** Dialog cu un camp text. Returneaza textul sau null. */
export function promptDialog({ title, body = '', label, placeholder = '', confirmText = 'Trimite', minLength = 0, maxLength = 500, multiline = false, danger = false }) {
  return modal({
    title,
    content: html`
      ${body ? html`<p class="muted">${body}</p>` : ''}
      <form class="field" id="prompt-form" novalidate>
        <label for="prompt-input">${label}</label>
        ${multiline
          ? html`<textarea class="textarea" id="prompt-input" maxlength="${maxLength}" placeholder="${placeholder}" required></textarea>`
          : html`<input class="input" id="prompt-input" maxlength="${maxLength}" placeholder="${placeholder}" autocomplete="off" required>`}
        <span class="error" hidden></span>
      </form>`,
    footer: html`
      <button class="btn btn-ghost" type="button" data-close>Renunță</button>
      <button class="btn ${danger ? 'btn-danger' : 'btn-primary'}" type="submit" form="prompt-form">${confirmText}</button>`,
    onMount: (dlg, close) => {
      const form = qs('#prompt-form', dlg);
      const input = qs('#prompt-input', dlg);
      const err = qs('.error', dlg);
      form.addEventListener('submit', (e) => {
        e.preventDefault();
        const v = input.value.trim();
        if (v.length < minLength) {
          err.textContent = `Minimum ${minLength} caractere.`;
          err.hidden = false;
          input.setAttribute('aria-invalid', 'true');
          input.focus();
          return;
        }
        close(v);
      });
    },
  }).then((v) => (typeof v === 'string' ? v : null));
}

// ------------------------------------------------------------------ clipboard / share

export async function copyText(text, message = 'Copiat în clipboard.') {
  try {
    await navigator.clipboard.writeText(text);
    toastOk(message, { icon: '📋', timeout: 2500 });
    return true;
  } catch {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.setAttribute('readonly', '');
    ta.className = 'sr-only';
    document.body.append(ta);
    ta.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch { ok = false; }
    ta.remove();
    if (ok) toastOk(message, { icon: '📋', timeout: 2500 });
    else toastError('Nu am putut copia automat. Selectează și copiază manual.');
    return ok;
  }
}

export async function shareLink({ title, text, url }) {
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url });
      return;
    } catch (e) {
      if (e?.name === 'AbortError') return;
    }
  }
  await copyText(url, 'Link copiat. Trimite-l prietenilor!');
}

/** Descarca un Blob ca fisier. */
export function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.dataset.external = '';
  document.body.append(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 4000);
}

// ------------------------------------------------------------------ confetti

export function confetti({ count = 140, duration = 2600 } = {}) {
  if (reducedMotion()) return;
  let canvas = document.getElementById('confetti');
  if (!canvas) {
    canvas = document.createElement('canvas');
    canvas.id = 'confetti';
    canvas.setAttribute('aria-hidden', 'true');
    document.body.append(canvas);
  }
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  const W = window.innerWidth;
  const H = window.innerHeight;
  canvas.width = W * dpr;
  canvas.height = H * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
  const colors = ['#7c3aed', '#d946ef', '#f59e0b', '#22c55e', '#3b82f6', '#f43f5e', '#fde68a'];
  const parts = Array.from({ length: count }, () => ({
    x: W / 2 + (Math.random() - 0.5) * W * 0.3,
    y: H * 0.35,
    vx: (Math.random() - 0.5) * 14,
    vy: -Math.random() * 14 - 4,
    s: Math.random() * 7 + 4,
    r: Math.random() * Math.PI,
    vr: (Math.random() - 0.5) * 0.3,
    c: colors[(Math.random() * colors.length) | 0],
    shape: Math.random() > 0.5 ? 'rect' : 'circle',
  }));
  const start = performance.now();
  const tick = (t) => {
    const elapsed = t - start;
    ctx.clearRect(0, 0, W, H);
    for (const p of parts) {
      p.vy += 0.35;
      p.vx *= 0.99;
      p.x += p.vx;
      p.y += p.vy;
      p.r += p.vr;
      ctx.save();
      ctx.globalAlpha = Math.max(0, 1 - elapsed / duration);
      ctx.translate(p.x, p.y);
      ctx.rotate(p.r);
      ctx.fillStyle = p.c;
      if (p.shape === 'rect') ctx.fillRect(-p.s / 2, -p.s / 4, p.s, p.s / 2);
      else { ctx.beginPath(); ctx.arc(0, 0, p.s / 3, 0, Math.PI * 2); ctx.fill(); }
      ctx.restore();
    }
    if (elapsed < duration) requestAnimationFrame(tick);
    else canvas.remove();
  };
  requestAnimationFrame(tick);
}

/** Text simplu pentru atribute title/aria (fara HTML). */
export const plain = (s) => esc(s);
