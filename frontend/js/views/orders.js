import { html, mount, join, qs, on } from '../dom.js';
import { get, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, plural } from '../format.js';
import { apAmount, emptyState, orderStatusTag, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { busy, handleError } from '../ui.js';

export default async function orders({ root, signal, alive }) {
  setMeta({ title: 'Comenzile mele', noindex: true });
  mount(root, skeletonPage());
  let page = 0;
  let items = [];
  let totalPages = 1;

  async function load() {
    const r = await get(`/orders?page=${page}&size=10`, { signal });
    items = items.concat(r.items || []);
    totalPages = r.totalPages || 1;
  }

  function render() {
    mount(root, html`<div class="page">
      <div class="page-head"><div><h1>Comenzile mele</h1><p>Urmărește livrarea, vezi codurile de voucher și anulează comenzile neexpediate.</p></div></div>
      ${items.length ? html`<div class="stack" id="orders-list">${join(items, card)}</div>
        ${page + 1 < totalPages ? html`<div class="load-more"><button class="btn btn-secondary" type="button" data-more>Comenzi mai vechi</button></div>` : ''}`
      : emptyState({ icon: '📦', title: 'Nu ai comenzi încă', text: 'Primul premiu e la un click distanță. Ai deja AP de bun venit!', action: html`<a class="btn btn-primary" href="/magazin">Mergi la magazin</a>` })}
    </div>`);
  }

  try {
    await load();
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;
  render();

  return on(root, 'click', '[data-more]', (e, b) => busy(b, async () => {
    page += 1;
    try {
      await load();
      if (alive()) render();
    } catch (err) {
      page -= 1;
      handleError(err);
    }
  }));
}

function card(o) {
  const names = o.items.map((i) => (i.quantity > 1 ? `${i.quantity}× ${i.name}` : i.name)).join(', ');
  return html`<a class="card card-link order-card" href="/comenzi/${encodeURIComponent(o.id)}">
    <div class="row-between">
      <div class="row"><strong class="mono">${o.id}</strong>${orderStatusTag(o.status, o.statusLabel)}</div>
      <span class="small muted">${date(o.createdAt)}</span>
    </div>
    <div class="order-items ellipsis">${names}</div>
    <div class="row-between">
      <span class="small muted">${plural(o.itemCount, 'produs', 'produse')}${o.requiresShipping ? ' · livrare prin curier' : ' · digital'}${o.trackingCode ? ` · AWB ${o.trackingCode}` : ''}</span>
      <span class="row">${apAmount(o.total)} ${icon('chevronRight')}</span>
    </div>
  </a>`;
}

