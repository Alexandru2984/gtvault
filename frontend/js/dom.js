// Sabloane HTML cu escaping implicit. Orice valoare interpolata in html`` este escapata,
// cu exceptia celor marcate explicit cu raw() (folosit doar pentru SVG-uri si fragmente generate de noi).

const ESCAPES = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;', '`': '&#96;' };

export function esc(value) {
  return String(value ?? '').replace(/[&<>"'`]/g, (c) => ESCAPES[c]);
}

export class SafeHtml {
  constructor(s) { this.s = s; }
  toString() { return this.s; }
}

export const raw = (s) => new SafeHtml(String(s ?? ''));

function part(v) {
  if (v == null || v === false || v === true) return '';
  if (v instanceof SafeHtml) return v.s;
  if (Array.isArray(v)) return v.map(part).join('');
  return esc(v);
}

export function html(strings, ...values) {
  let out = strings[0];
  for (let i = 0; i < values.length; i++) out += part(values[i]) + strings[i + 1];
  return new SafeHtml(out);
}

/** Uneste o lista de fragmente. */
export const join = (items, fn) => raw((items || []).map((x, i) => part(fn(x, i))).join(''));

export function internalPath(value) {
  if (typeof value !== 'string' || !/^\/(?!\/)/.test(value) || /[\\\x00-\x20\x7f]/.test(value)) return null;
  const url = new URL(value, location.origin);
  if (url.origin !== location.origin || url.pathname.startsWith('//')) return null;
  return url.pathname + url.search + url.hash;
}

/** Link-urile vechi din notificari au prefixul #/. */
export const safeHref = (url) => internalPath(url?.startsWith('#/') ? url.slice(1) : url) || '#';

const IMG_PATH = /^images\/[a-z0-9/_-]{1,100}\.(svg|png|jpe?g|webp)$/;
export const DEFAULT_AVATAR = '/images/user-avatar.svg';
export const PLACEHOLDER_IMG = '/images/gt-logo.svg';
const PRODUCT_IMAGES = {
  'rew-agenda': 'agenda', 'rew-alltracks': 'tracks', 'rew-badge': 'badge', 'rew-ghidgpt': 'chatgpt',
  'rew-lucky': 'lucky', 'rew-meet': 'meet', 'rew-mentorat': 'mentorat', 'rew-mouse': 'mouse',
  'rew-sticker': 'sticker', 'rew-templatecv': 'cv', 'rew-tricou': 'tricou', 'rew-udemy': 'udemy',
};

/** Caile de imagini din API sunt relative ("images/..."); le validam si le facem absolute. */
export function imgSrc(path, fallback = PLACEHOLDER_IMG) {
  const p = String(path ?? '').trim();
  return IMG_PATH.test(p) ? `/${p}` : fallback;
}

export const avatarSrc = (path) => imgSrc(path, DEFAULT_AVATAR);

export function productImage(reward) {
  const id = reward.rewardId ?? reward.id;
  const image = Object.hasOwn(PRODUCT_IMAGES, id) ? `/images/products/${PRODUCT_IMAGES[id]}.svg` : PLACEHOLDER_IMG;
  return imgSrc(reward.image, image);
}

/** Variabile CSS pentru elemente randate: data-vars="--p:40%;--c:#ff0000". */
const VAR_NAME = /^--[a-z][a-z0-9-]*$/;
const VAR_VALUE = /^[#a-zA-Z0-9.,%()\s-]{1,64}$/;

export function vars(obj) {
  return Object.entries(obj)
    .filter(([, v]) => v != null)
    .map(([k, v]) => `${k}:${v}`)
    .join(';');
}

export function applyVars(root) {
  if (!root || !root.querySelectorAll) return;
  const nodes = root.matches?.('[data-vars]') ? [root, ...root.querySelectorAll('[data-vars]')] : root.querySelectorAll('[data-vars]');
  for (const el of nodes) {
    for (const decl of el.getAttribute('data-vars').split(';')) {
      const i = decl.indexOf(':');
      if (i < 0) continue;
      const name = decl.slice(0, i).trim();
      const value = decl.slice(i + 1).trim();
      if (VAR_NAME.test(name) && VAR_VALUE.test(value)) el.style.setProperty(name, value);
    }
    el.removeAttribute('data-vars');
  }
}

/** Inlocuieste continutul unui element cu un sablon html``. */
export function mount(el, tpl) {
  el.innerHTML = tpl instanceof SafeHtml ? tpl.s : esc(tpl);
  applyVars(el);
  return el;
}

/** Creeaza un element dintr-un sablon (primul nod). */
export function fragment(tpl) {
  const t = document.createElement('template');
  t.innerHTML = tpl instanceof SafeHtml ? tpl.s : esc(tpl);
  applyVars(t.content);
  return t.content.firstElementChild;
}

export const qs = (sel, root = document) => root.querySelector(sel);
export const qsa = (sel, root = document) => Array.from(root.querySelectorAll(sel));

/** Delegare de evenimente. Returneaza functia de dezabonare. */
export function on(root, type, selector, handler, options) {
  const fn = (e) => {
    const target = e.target instanceof Element ? e.target.closest(selector) : null;
    if (target && root.contains(target)) handler(e, target);
  };
  root.addEventListener(type, fn, options);
  return () => root.removeEventListener(type, fn, options);
}

/** Datele unui formular ca obiect simplu (valori text taiate la capete). */
export function formData(form) {
  const out = {};
  for (const [k, v] of new FormData(form).entries()) out[k] = typeof v === 'string' ? v.trim() : v;
  return out;
}
