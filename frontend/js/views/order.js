import { html, mount, join, on, qs } from '../dom.js';
import { get, post, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, dateTime, num } from '../format.js';
import { apAmount, emptyState, orderStatusTag, skeletonPage, tierName, FULFILLMENT } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { refreshUser, state } from '../store.js';
import { busy, modal, copyText, handleError, toastOk } from '../ui.js';
import { repeatOrder } from '../order-repeat.js';

const STEP_ICON = { PLACED: 'clock', PROCESSING: 'package', SHIPPED: 'truck', DELIVERED: 'check', FULFILLED: 'check', CANCELLED: 'x' };

export default async function order({ root, params, query, signal, alive }) {
  const token = state.token;
  let disposed = false;
  const active = () => !disposed && alive() && state.token === token;
  const dialogs = new Set();
  setMeta({ title: `Comanda ${params.id}`, noindex: true });
  mount(root, skeletonPage());
  let o;
  try {
    o = await get(`/orders/${enc(params.id)}`, { signal });
  } catch (err) {
    if (!active() || isAbort(err)) return;
    if (err.status === 404) {
      mount(root, emptyState({ icon: '🔎', title: 'Comanda nu a fost găsită', action: html`<a class="btn btn-primary" href="/comenzi">Comenzile mele</a>` }));
      return;
    }
    throw err;
  }
  if (!active()) return;
  const fresh = query.nou === '1';
  function openDialog(options) {
    return modal({ ...options, onMount: (dlg, close) => {
      dialogs.add(close);
      dlg.addEventListener('close', () => dialogs.delete(close), { once: true });
      options.onMount?.(dlg, close);
    } });
  }

  function render() {
    if (!active()) return;
    const delivered = o.status === 'DELIVERED' || o.status === 'FULFILLED';
    mount(root, html`<div class="page">
      <nav class="breadcrumbs" aria-label="Breadcrumb"><a href="/comenzi">Comenzile mele</a>${icon('chevronRight')}<span aria-current="page">${o.id}</span></nav>
      ${fresh && o.status !== 'CANCELLED' ? html`<section class="card success-banner reveal">
        <div class="big" aria-hidden="true">🎉</div>
        <h2>Comandă plasată!</h2>
        <p class="muted">${o.requiresShipping ? 'Te anunțăm când pleacă spre tine. Urmărești totul pe pagina asta.' : 'Codurile tale de voucher sunt mai jos. Spor la folosit!'}</p>
      </section>` : ''}
      <div class="page-head">
        <div><h1 class="mono">${o.id}</h1><p>Plasată pe ${dateTime(o.createdAt)}</p></div>
        <div class="row">${orderStatusTag(o.status, o.statusLabel)}</div>
      </div>
      <div class="cart-layout">
        <div class="stack stack-lg">
          <section class="card card-pad stack" aria-labelledby="items-h">
            <h2 class="card-title" id="items-h">${icon('package')} Produse</h2>
            ${join(o.items, (i) => html`<div class="stack stack-sm">
              <div class="row-between">
                <a class="strong" href="/produs/${enc(i.rewardId)}">${i.quantity > 1 ? `${i.quantity}× ` : ''}${i.name}</a>
                <span class="strong">${num(i.pointsSpent)} AP</span>
              </div>
              <span class="xsmall muted">${FULFILLMENT[i.fulfillment]?.label || ''}${i.flashUnits ? ` · ${i.flashUnits} la ofertă flash` : ''}</span>
              ${i.voucherCode ? html`<div class="voucher">
                <code>${i.voucherCode}</code>
                ${i.redeemed ? html`<span class="tag tag-muted">Folosit</span>` : html`<button class="btn btn-secondary btn-sm" type="button" data-copy="${i.voucherCode}">${icon('copy')} Copiază</button>`}
              </div>` : ''}
              ${delivered && i.fulfillment !== 'BADGE' ? html`<a class="small strong" href="/produs/${enc(i.rewardId)}#recenzii">⭐ Lasă o recenzie și primești AP</a>` : ''}
            </div>`)}
          </section>

          ${o.requiresShipping ? html`<section class="card card-pad stack" aria-labelledby="ship-h">
            <h2 class="card-title" id="ship-h">${icon('truck')} Livrare</h2>
            ${o.shipping ? html`<address class="small">
              <strong>${o.shipping.name}</strong><br>${o.shipping.address}<br>${o.shipping.city}, jud. ${o.shipping.county}, ${o.shipping.postalCode}<br>${o.shipping.phone}
            </address>` : ''}
            ${o.trackingCode ? html`<div class="voucher"><span class="small">AWB: <code>${o.trackingCode}</code></span><button class="btn btn-secondary btn-sm" type="button" data-copy="${o.trackingCode}">${icon('copy')} Copiază</button></div>` : ''}
            ${o.notes ? html`<p class="small muted">Observații: ${o.notes}</p>` : ''}
          </section>` : o.notes ? html`<section class="card card-pad"><p class="small muted">Observații: ${o.notes}</p></section>` : ''}
        </div>

        <aside class="stack stack-lg summary-sticky">
          <section class="card card-pad stack" aria-labelledby="tl-h">
            <h2 class="card-title" id="tl-h">${icon('clock')} Stadiu</h2>
            <ol class="timeline">${join(o.timeline, (s) => html`<li class="tl-step ${s.status === 'CANCELLED' ? 'is-cancelled' : s.current ? 'is-current' : s.reached ? 'is-done' : ''}">
              <span class="tl-dot">${icon(STEP_ICON[s.status] || 'clock')}</span>
              <div><div class="tl-label">${s.label}</div>${s.at ? html`<div class="tl-at">${dateTime(s.at)}</div>` : ''}</div>
            </li>`)}</ol>
          </section>
          <section class="card card-pad stack" aria-labelledby="pay-h">
            <h2 class="card-title" id="pay-h">${icon('tag')} Plată</h2>
            <dl class="dl">
              <dt>Subtotal</dt><dd>${num(o.subtotal)} AP</dd>
              ${o.flashDiscount ? html`<dt>Oferte flash</dt><dd class="discount">−${num(o.flashDiscount)} AP</dd>` : ''}
              ${o.tierDiscount ? html`<dt>Reducere ${tierName(o.tier)}</dt><dd class="discount">−${num(o.tierDiscount)} AP</dd>` : ''}
              ${o.promoDiscount ? html`<dt>Cod ${o.promoCode}</dt><dd class="discount">−${num(o.promoDiscount)} AP</dd>` : ''}
              <dt class="total">Total</dt><dd class="total">${apAmount(o.total)}</dd>
            </dl>
            ${o.status === 'CANCELLED' ? html`<div class="callout is-success">${icon('check')}<span>Cei ${num(o.total)} AP au fost returnați în contul tău.</span></div>` : ''}
            ${o.canCancel ? html`<button class="btn btn-danger-soft btn-block" type="button" data-cancel>${icon('x')} Anulează comanda</button>` : ''}
            <button class="btn btn-secondary btn-block" type="button" data-repeat>Adaugă din nou în coș</button>
          </section>
          <p class="xsmall muted">Ultima actualizare: ${date(o.updatedAt)}</p>
        </aside>
      </div>
    </div>`);
  }

  render();
  if (fresh) setQuery({}); // un refresh al paginii nu mai arata bannerul de succes

  const off = on(root, 'click', '[data-copy], [data-cancel], [data-repeat]', async (e, b) => {
    if (!active()) return;
    if (b.hasAttribute('data-repeat')) {
      await busy(b, () => repeatOrder({ orderId: o.id, signal, active, openDialog }));
      return;
    }
    if (b.dataset.copy) {
      copyText(b.dataset.copy);
      return;
    }
    const ok = await openDialog({
      title: 'Anulezi comanda?',
      content: html`<p class="muted">Primești înapoi integral ${num(o.total)} AP, iar produsele revin în stoc. Acțiunea nu poate fi anulată.</p>`,
      footer: html`<button class="btn btn-ghost" type="button" data-close>Păstrează comanda</button><button class="btn btn-danger" type="button" data-confirm-cancel>Da, anulează</button>`,
      onMount: (dlg, close) => {
        qs('[data-confirm-cancel]', dlg).addEventListener('click', () => close(true));
        qs('[data-confirm-cancel]', dlg).focus();
      },
    });
    if (ok !== true || !active()) return;
    await busy(b, async () => {
      try {
        const result = await post(`/orders/${enc(o.id)}/cancel`, null, { signal });
        if (!active()) return;
        o = result;
        toastOk(`Comanda a fost anulată. ${num(o.total)} AP au revenit în cont.`, { icon: '↩️' });
        refreshUser();
        render();
      } catch (err) { if (active() && !isAbort(err)) handleError(err); }
    });
  });
  return () => {
    disposed = true;
    for (const close of [...dialogs]) close();
    off();
  };
}
