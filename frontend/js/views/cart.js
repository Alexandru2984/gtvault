import { html, mount, join, qs, on, productImage } from '../dom.js';
import { get, post, put, del, enc, isAbort } from '../api.js';
import { icon, coin } from '../icons.js';
import { num, plural } from '../format.js';
import { apAmount, emptyState, skeletonPage, FULFILLMENT, tierName } from '../components.js';
import { setMeta } from '../seo.js';
import { setCartCount } from '../store.js';
import { busy, confirmDialog, handleError, toastOk } from '../ui.js';

const PROMO_KEY = 'gt.promo';

export function storedPromo() {
  try { return sessionStorage.getItem(PROMO_KEY) || ''; } catch { return ''; }
}
export function storePromo(code) {
  try {
    if (code) sessionStorage.setItem(PROMO_KEY, code);
    else sessionStorage.removeItem(PROMO_KEY);
  } catch { /* ignorat */ }
}

/** Cotatia curenta a cosului (cu codul promo memorat, daca exista). */
export function fetchQuote(signal) {
  const promo = storedPromo();
  return promo ? post('/checkout/quote', { promoCode: promo }, { signal }) : get('/cart', { signal });
}

export function summary(q, { showPromoForm = true } = {}) {
  const promo = q.promo;
  return html`<div class="stack">
    <dl class="dl">
      <dt>Subtotal (${plural(q.itemCount, 'produs', 'produse')})</dt><dd>${num(q.subtotal)} AP</dd>
      ${q.flashDiscount ? html`<dt>Oferte flash</dt><dd class="discount">−${num(q.flashDiscount)} AP</dd>` : ''}
      ${q.tierDiscount ? html`<dt>Reducere nivel ${tierName(q.tier)} (${q.tierPercent}%)</dt><dd class="discount">−${num(q.tierDiscount)} AP</dd>` : ''}
      ${q.promoDiscount ? html`<dt>Cod ${promo?.code}</dt><dd class="discount">−${num(q.promoDiscount)} AP</dd>` : ''}
      <dt class="total">Total</dt><dd class="total">${apAmount(q.total)}</dd>
    </dl>
    ${showPromoForm ? promoBlock(promo) : ''}
    <div class="balance-line ${q.missingPoints > 0 ? 'is-short' : ''}">
      <span>${q.missingPoints > 0 ? `Îți lipsesc ${num(q.missingPoints)} AP` : 'Sold după comandă'}</span>
      <strong>${q.missingPoints > 0 ? html`Sold: ${num(q.balance)} AP` : html`${num(q.balance - q.total)} AP`}</strong>
    </div>
  </div>`;
}

function promoBlock(promo) {
  if (promo?.valid) {
    return html`<div class="promo-ok"><span>${icon('tag')} ${promo.code} · −${num(promo.discount)} AP</span><button class="link-btn small" type="button" data-promo-remove>Elimină</button></div>`;
  }
  return html`<form class="stack stack-sm" data-promo-form novalidate>
    <label class="field-label" for="promo">Ai un cod promoțional?</label>
    <div class="promo-row">
      <input class="input" id="promo" name="code" placeholder="EX: GT2026" maxlength="32" autocomplete="off" autocapitalize="characters" value="${promo?.code || ''}" aria-invalid="${Boolean(promo && !promo.valid)}">
      <button class="btn btn-secondary" type="submit">Aplică</button>
    </div>
    ${promo && !promo.valid ? html`<span class="error small">${promo.message}</span>` : ''}
  </form>`;
}

export default async function cart({ root, signal, alive }) {
  setMeta({ title: 'Coșul tău', noindex: true });
  mount(root, skeletonPage());

  let q;
  async function refresh() {
    try {
      q = await fetchQuote(signal);
    } catch (err) {
      if (isAbort(err)) return;
      // cod promo devenit invalid / expirat: il scoatem si reincercam fara el
      if (storedPromo()) {
        storePromo('');
        q = await get('/cart', { signal });
      } else throw err;
    }
    if (!alive()) return;
    setCartCount(q.itemCount);
    render();
  }

  function render() {
    if (!q.lines?.length) {
      storePromo('');
      mount(root, html`<div class="page"><h1>Coșul tău</h1>${emptyState({
        icon: '🛒', title: 'Coșul e gol',
        text: 'Răsfoiește premiile și adaugă ce-ți place. Ai AP de cheltuit!',
        action: html`<a class="btn btn-primary" href="/magazin">${icon('bag')} Mergi la magazin</a>`,
      })}</div>`);
      return;
    }
    const blocked = q.issues?.length > 0 || q.missingPoints > 0;
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Coșul tău</h1><p>${plural(q.itemCount, 'produs', 'produse')} · ${q.requiresShipping ? 'conține produse cu livrare' : 'doar produse digitale, livrare instant'}</p></div>
        <button class="btn btn-ghost btn-sm" type="button" data-clear>${icon('trash')} Golește coșul</button>
      </div>
      ${q.issues?.length ? html`<div class="callout is-danger">${icon('alert')}<div>${join(q.issues, (i) => html`<div>${i}</div>`)}</div></div>` : ''}
      <div class="cart-layout">
        <section class="card" aria-label="Produse în coș">${join(q.lines, line)}</section>
        <aside class="card card-pad stack summary-sticky" aria-labelledby="sum-h">
          <h2 id="sum-h" class="card-title">Sumar</h2>
          ${summary(q)}
          ${blocked ? html`<button class="btn btn-primary btn-lg btn-block" type="button" disabled>Finalizează comanda ${icon('chevronRight')}</button>`
            : html`<a class="btn btn-primary btn-lg btn-block" href="/finalizare">Finalizează comanda ${icon('chevronRight')}</a>`}
          ${q.missingPoints > 0 ? html`<a class="btn btn-ghost btn-block" href="/activitati">${icon('play')} Câștigă AP care îți lipsesc</a>` : ''}
          <p class="xsmall muted center">Prețurile și stocul sunt verificate din nou la plasarea comenzii.</p>
        </aside>
      </div>
    </div>`);
  }

  function line(l) {
    const max = Math.max(1, Math.min(l.maxPerOrder || 1, l.stockCount || 1));
    const f = FULFILLMENT[l.fulfillment];
    return html`<div class="cart-line ${l.issue ? 'has-issue' : ''}">
      <a href="/produs/${enc(l.rewardId)}" tabindex="-1" aria-hidden="true"><img src="${productImage(l)}" alt="" width="76" height="76" loading="lazy"></a>
      <div class="cart-line-main">
        <a class="cart-line-name" href="/produs/${enc(l.rewardId)}">${l.name}</a>
        <span class="xsmall muted">${f ? `${f.icon} ${f.label}` : ''} · ${num(l.unitPrice)} AP / buc.</span>
        ${l.dealUnits ? html`<span class="tag tag-hot">⚡ ${l.dealUnits} ${l.dealUnits === 1 ? 'bucată' : 'bucăți'} la ${num(l.dealUnitPrice)} AP</span>` : ''}
        ${l.issue ? html`<span class="small strong">${icon('alert')} ${l.issue}</span>` : ''}
        <div class="cart-line-foot">
          <div class="qty qty-sm" role="group" aria-label="Cantitate ${l.name}">
            <button type="button" data-set="${l.rewardId}" data-q="${l.quantity - 1}" aria-label="Scade" ${l.quantity <= 1 ? 'disabled' : ''}>${icon('minus')}</button>
            <output>${l.quantity}</output>
            <button type="button" data-set="${l.rewardId}" data-q="${l.quantity + 1}" aria-label="Crește" ${l.quantity >= max ? 'disabled' : ''}>${icon('plus')}</button>
          </div>
          <div class="row">
            <span class="price"><span class="price-now">${coin()}${num(l.lineTotal)}</span>${l.lineFlashDiscount ? html`<span class="price-old">${num(l.lineSubtotal)}</span>` : ''}</span>
            <button class="btn btn-ghost btn-sm btn-icon" type="button" data-remove="${l.rewardId}" aria-label="Elimină ${l.name}">${icon('trash')}</button>
          </div>
        </div>
      </div>
    </div>`;
  }

  const offs = [
    on(root, 'click', '[data-set]', (e, b) => busy(b, async () => {
      try {
        await put(`/cart/items/${enc(b.dataset.set)}`, { quantity: Number(b.dataset.q) });
        await refresh();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-remove]', (e, b) => busy(b, async () => {
      try {
        await del(`/cart/items/${enc(b.dataset.remove)}`);
        toastOk('Produs eliminat din coș.', { icon: '🗑️', timeout: 2200 });
        await refresh();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-clear]', async (e, b) => {
      if (!(await confirmDialog({ title: 'Golești coșul?', body: 'Toate produsele vor fi eliminate din coș.', confirmText: 'Golește', danger: true }))) return;
      await busy(b, async () => {
        try {
          await del('/cart');
          storePromo('');
          await refresh();
        } catch (err) { handleError(err); }
      });
    }),
    on(root, 'submit', '[data-promo-form]', async (e, form) => {
      e.preventDefault();
      const code = qs('#promo', form).value.trim().toUpperCase();
      if (!code) return;
      await busy(qs('[type=submit]', form), async () => {
        try {
          const res = await post('/promo/validate', { code });
          q = res;
          if (res.promo?.valid) {
            storePromo(code);
            toastOk(`Codul ${code} a fost aplicat: −${num(res.promo.discount)} AP`, { icon: '🏷️' });
          } else storePromo('');
          render();
        } catch (err) { handleError(err); }
      });
    }),
    on(root, 'click', '[data-promo-remove]', async () => {
      storePromo('');
      await refresh();
    }),
  ];

  await refresh();
  return () => offs.forEach((off) => off());
}
