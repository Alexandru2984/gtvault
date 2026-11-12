import { html, mount, join, on } from '../dom.js';
import { get, isAbort } from '../api.js';
import { dateTime, num, signed } from '../format.js';
import { apAmount, emptyState, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { state } from '../store.js';
import { busy, downloadBlob, handleError } from '../ui.js';
import { ledgerCsv } from '../csv.js';

const FILTERS = { all: 'Toate', earned: 'Câștigate', games: 'Jocuri', gifts: 'Cadouri', orders: 'Comenzi' };

export default async function history({ root, query, signal, alive }) {
  setMeta({ title: 'Istoric puncte', noindex: true });
  let filter = FILTERS[query.f] ? query.f : 'all';
  let page = 0;
  let items = [];
  let totalPages = 1;
  let totalItems = 0;
  mount(root, skeletonPage());

  async function load(reset) {
    if (reset) { page = 0; items = []; }
    const r = await get(`/user/transactions?page=${page}&size=25&filter=${filter}`, { signal });
    items = items.concat(r.items || []);
    totalPages = r.totalPages || 1;
    totalItems = r.totalItems || 0;
  }

  function render() {
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Istoric puncte</h1><p>Fiecare AP câștigat sau cheltuit, cu soldul după fiecare mișcare.</p></div>
        <div class="stack"><div class="kpi"><span class="kpi-label">Sold curent</span><span class="kpi-value">${apAmount(state.user?.activityPoints || 0)}</span></div>
          <button class="btn btn-secondary btn-sm" type="button" data-export-ledger>Descarcă tot istoricul CSV</button></div>
      </div>
      <div class="chips" role="group" aria-label="Filtru tranzacții">${join(Object.entries(FILTERS), ([k, v]) => html`<button class="chip" type="button" data-f="${k}" aria-pressed="${String(k === filter)}">${v}</button>`)}</div>
      ${items.length ? html`<section class="card" aria-label="${totalItems} tranzacții">${join(items, (t) => html`<div class="tx">
          <span class="tx-ico" aria-hidden="true">${t.icon || '•'}</span>
          <div class="ellipsis"><div class="strong ellipsis">${t.label}</div><div class="xsmall muted ellipsis">${t.description || ''} · ${dateTime(t.createdAt)}</div></div>
          <div><div class="tx-amt ${t.amount >= 0 ? 'is-plus' : 'is-minus'}">${signed(t.amount)} AP</div><div class="tx-bal">sold ${num(t.balanceAfter)}</div></div>
        </div>`)}</section>
        ${page + 1 < totalPages ? html`<div class="load-more"><button class="btn btn-secondary" type="button" data-more>Mai multe</button></div>` : ''}`
      : emptyState({ icon: '🧾', title: 'Nicio tranzacție', text: filter === 'all' ? 'Încă nu ai mișcări de puncte.' : 'Nicio tranzacție pentru filtrul ales.' })}
    </div>`);
  }

  try {
    await load(true);
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;
  render();

  const offs = [
    on(root, 'click', '[data-export-ledger]', (event, button) => busy(button, async () => {
      try {
        const data = await get('/user/export', { signal });
        if (alive()) downloadBlob(ledgerCsv(data.point_transactions || []), 'rewardhub-istoric-puncte.csv');
      } catch (error) { if (alive()) handleError(error); }
    })),
    on(root, 'click', '[data-f]', async (e, b) => {
      filter = b.dataset.f;
      setQuery({ f: filter === 'all' ? '' : filter });
      try { await load(true); if (alive()) render(); } catch (err) { handleError(err); }
    }),
    on(root, 'click', '[data-more]', (e, b) => busy(b, async () => {
      page += 1;
      try { await load(false); if (alive()) render(); } catch (err) { page -= 1; handleError(err); }
    })),
  ];
  return () => offs.forEach((off) => off());
}
