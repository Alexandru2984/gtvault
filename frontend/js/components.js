// Fragmente de interfata reutilizabile (functii pure care intorc sabloane html``).
import { html, raw, productImage, avatarSrc, vars, join } from './dom.js';
import { icon, coin } from './icons.js';
import { num, rating as fmtRating, plural } from './format.js';
import { state, isAuthed } from './store.js';
import { comparedIds } from './compare.js';

export const apAmount = (n, cls = '') => html`<span class="ap ${cls}">${coin()}${num(n)}<span class="sr-only"> AP</span></span>`;

export function stars(value, label = true) {
  const pct = value == null ? 0 : Math.max(0, Math.min(100, (Number(value) / 5) * 100));
  return html`<span class="stars" data-vars="${vars({ '--pct': `${pct.toFixed(1)}%` })}" ${label ? html`role="img" aria-label="Notă ${value == null ? 'indisponibilă' : fmtRating(value)} din 5"` : raw('aria-hidden="true"')}></span>`;
}

export function ratingInline(r, count) {
  if (!count) return html`<span class="rating">Fără recenzii încă</span>`;
  return html`<span class="rating">${stars(r)} <strong>${fmtRating(r)}</strong> (${num(count)})</span>`;
}

export const avatar = (path, name = '', size = '') =>
  html`<img class="avatar ${size ? `avatar-${size}` : ''}" src="${avatarSrc(path)}" alt="${name ? `Avatar ${name}` : ''}" width="40" height="40" loading="lazy" decoding="async">`;

export const TIERS = {
  BRONZE: { label: 'Bronze', icon: '🥉' },
  SILVER: { label: 'Silver', icon: '🥈' },
  GOLD: { label: 'Gold', icon: '🥇' },
  PLATINUM: { label: 'Platinum', icon: '💠' },
  DIAMOND: { label: 'Diamond', icon: '💎' },
};

export const tierName = (code) => TIERS[code]?.label || code || '';

export function tierTag(code, iconOverride) {
  if (!code) return '';
  const t = TIERS[code] || { label: code, icon: '🏅' };
  return html`<span class="tag tag-brand">${iconOverride || t.icon} ${t.label}</span>`;
}

export const FULFILLMENT = {
  PHYSICAL: { label: 'Livrare fizică', icon: '📦', hint: 'Îl primești prin curier la adresa ta.' },
  DIGITAL: { label: 'Digital · voucher', icon: '⚡', hint: 'Primești instant un cod de voucher în comandă.' },
  BADGE: { label: 'Insignă pe profil', icon: '🏅', hint: 'Apare imediat pe profilul tău public.' },
};

export function priceBlock(r) {
  if (r.deal) {
    return html`<div class="price">
      <span class="price-old">${num(r.price)} AP</span>
      <span class="price-now is-deal">${coin()}${num(r.deal.dealPrice)}<span class="sr-only"> AP, redus de la ${num(r.price)} AP</span></span>
    </div>`;
  }
  return html`<div class="price"><span class="price-now">${coin()}${num(r.price)}<span class="sr-only"> AP</span></span></div>`;
}

export function productBadges(r) {
  return html`<div class="pcard-badges">
    ${r.deal ? html`<span class="tag tag-hot">${icon('zap')} −${r.deal.discountPercent}%</span>` : ''}
    ${r.isNew ? html`<span class="tag tag-brand">Nou</span>` : ''}
    ${!r.inStock ? html`<span class="tag tag-solid">Epuizat</span>` : r.lowStock ? html`<span class="tag tag-warning">Stoc redus</span>` : ''}
    ${r.minTier ? html`<span class="tag tag-gold">${icon('lock')} ${tierName(r.minTier)}+</span>` : ''}
  </div>`;
}

export function wishButton(id, name) {
  const on = state.wishlist.has(id);
  return html`<button class="wish-btn" type="button" data-wish="${id}" aria-pressed="${on}" aria-label="${on ? 'Scoate' : 'Adaugă'} „${name}” ${on ? 'din' : 'în'} favorite">${icon('heart')}</button>`;
}

export function productCard(r) {
  const href = `/produs/${encodeURIComponent(r.id)}`;
  return html`<article class="pcard ${r.inStock ? '' : 'is-out'}">
    <a class="pcard-media" href="${href}" tabindex="-1" aria-hidden="true">
      <img src="${productImage(r)}" alt="" width="300" height="258" loading="lazy" decoding="async">
    </a>
    ${productBadges(r)}
    ${wishButton(r.id, r.name)}
    <div class="pcard-body">
      <span class="pcard-cat">${r.category}</span>
      <a class="pcard-title" href="${href}">${r.name}</a>
      <div class="pcard-meta">
        ${r.reviewCount ? html`${stars(r.rating)} <span>${fmtRating(r.rating)} (${num(r.reviewCount)})</span>` : html`<span>${FULFILLMENT[r.fulfillment]?.icon || ''} ${FULFILLMENT[r.fulfillment]?.label || ''}</span>`}
      </div>
      ${r.inStock && r.lowStock ? html`<span class="pcard-stock">Doar ${plural(r.stockCount, 'bucată rămasă', 'bucăți rămase')}</span>` : ''}
      <div class="pcard-foot">
        ${priceBlock(r)}
        ${r.inStock
          ? html`<button class="btn btn-secondary btn-sm btn-icon" type="button" data-add-cart="${r.id}" aria-label="Adaugă „${r.name}” în coș">${icon('plus')}</button>`
          : ''}
      </div>
      ${compareButton(r)}
    </div>
  </article>`;
}

export function compareButton(reward) {
  return html`<button class="btn btn-ghost btn-sm" type="button" data-compare="${reward.id}" aria-pressed="${comparedIds().includes(reward.id)}" aria-label="Compară ${reward.name}">${icon('sliders')} Compară</button>`;
}

export const productGrid = (list) => html`<div class="products">${join(list, productCard)}</div>`;

export function emptyState({ icon: ico = '🗂️', title, text = '', action = null }) {
  return html`<div class="empty">
    <div class="empty-icon" aria-hidden="true">${ico}</div>
    <h3>${title}</h3>
    ${text ? html`<p>${text}</p>` : ''}
    ${action || ''}
  </div>`;
}

export function skeletonGrid(n = 8) {
  return html`<div class="products" aria-hidden="true">${join(Array.from({ length: n }), () => html`<div class="skel skel-card"></div>`)}</div>`;
}

export function skeletonPage() {
  return html`<div class="page" aria-busy="true" aria-label="Se încarcă">
    <div class="skel skel-title"></div>
    <div class="skel skel-line"></div>
    <div class="skel skel-block"></div>
    <div class="grid-2"><div class="skel skel-block"></div><div class="skel skel-block"></div></div>
  </div>`;
}

export const ORDER_STATUS = {
  PLACED: { label: 'Plasată', cls: 'tag-info', icon: 'clock' },
  PROCESSING: { label: 'În pregătire', cls: 'tag-warning', icon: 'package' },
  SHIPPED: { label: 'Expediată', cls: 'tag-brand', icon: 'truck' },
  DELIVERED: { label: 'Livrată', cls: 'tag-success', icon: 'check' },
  FULFILLED: { label: 'Finalizată', cls: 'tag-success', icon: 'check' },
  CANCELLED: { label: 'Anulată', cls: 'tag-danger', icon: 'x' },
};

export function orderStatusTag(status, label) {
  const s = ORDER_STATUS[status] || { label: status, cls: '', icon: 'info' };
  return html`<span class="tag ${s.cls}">${icon(s.icon)} ${label || s.label}</span>`;
}

export const PROPOSAL_STATUS = {
  PENDING: { label: 'În analiză', cls: 'tag-info' },
  APPROVED: { label: 'Aprobată', cls: 'tag-success' },
  IMPLEMENTED: { label: 'Implementată', cls: 'tag-brand' },
  REJECTED: { label: 'Respinsă', cls: 'tag-danger' },
};

export function countdownEl(iso, { prefix = '', className = 'countdown' } = {}) {
  return html`<span class="${className}" data-countdown="${iso}" data-prefix="${prefix}">${icon('clock')}<span class="cd-text">…</span></span>`;
}

export function loginPrompt(text = 'Intră în cont ca să participi.', next = location.pathname) {
  return html`<div class="callout">${icon('lock')}<div class="grow">${text}
    <div class="row mt-1">
      <a class="btn btn-primary btn-sm" href="/autentificare?next=${encodeURIComponent(next)}">Intră în cont</a>
      <a class="btn btn-ghost btn-sm" href="/inregistrare?next=${encodeURIComponent(next)}">Creează cont · +1.000 AP</a>
    </div></div></div>`;
}

export const authed = isAuthed;

/** Bara de progres; p in procente 0..100. */
export const progress = (p, cls = '', label = '') =>
  html`<div class="progress ${cls}" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${Math.round(p)}" ${label ? html`aria-label="${label}"` : ''} data-vars="${vars({ '--p': `${Math.max(0, Math.min(100, p))}%` })}"><span></span></div>`;
