import { html, mount, join, qs, on, formData } from '../dom.js';
import { get, query as queryString, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, num, plural } from '../format.js';
import { apAmount, emptyState, orderStatusTag, skeletonPage, ORDER_STATUS } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { state } from '../store.js';

const DEFAULTS = { q: '', status: 'ALL', delivery: 'ALL', from: '', to: '', sort: 'NEWEST' };
const choice = (value, allowed, fallback) => allowed.includes(String(value || '').toUpperCase()) ? String(value).toUpperCase() : fallback;

export default async function orders({ root, query = {}, signal, alive }) {
  setMeta({ title: 'Comenzile mele', noindex: true });
  const token = state.token;
  let disposed = false;
  let generation = 0;
  let controller = null;
  const active = () => !disposed && alive() && state.token === token;
  let filters = {
    q: String(query.q || '').slice(0, 80),
    status: choice(query.status, ['ALL', ...Object.keys(ORDER_STATUS)], 'ALL'),
    delivery: choice(query.delivery, ['ALL', 'PHYSICAL', 'DIGITAL'], 'ALL'),
    from: String(query.from || ''), to: String(query.to || ''),
    sort: choice(query.sort, ['NEWEST', 'OLDEST'], 'NEWEST'),
  };
  let page = /^\d{1,10}$/.test(query.page || '') ? Math.min(2147483647, Number(query.page)) : 0;
  const advanced = Object.keys(DEFAULTS).some((key) => key !== 'q' && filters[key] !== DEFAULTS[key]);
  const options = (entries, selected) => join(entries, ([value, label]) => html`<option value="${value}" ${value === selected ? 'selected' : ''}>${label}</option>`);
  mount(root, html`<div class="page">
    <div class="page-head"><div><h1>Comenzile mele</h1><p>Găsește premiile comandate și urmărește livrarea. Codurile de voucher și adresa sunt în detaliile comenzii.</p></div><a class="btn btn-secondary" href="/vouchere">Verifică un voucher</a></div>
    <form class="card card-pad stack orders-filters" data-orders-filters role="search" aria-label="Caută și filtrează comenzile">
      <div class="field"><label for="orders-search">Caută în comenzile mele</label><input class="input" id="orders-search" name="q" type="search" maxlength="80" value="${filters.q}" placeholder="Numele produsului sau numărul comenzii" autocomplete="off" enterkeyhint="search"></div>
      <details ${advanced ? 'open' : ''} data-orders-advanced><summary class="strong" data-orders-filter-label>Filtre și sortare</summary><div class="orders-filter-grid mt-1">
        <div class="field"><label for="orders-status">Statusul comenzii</label><select class="select" id="orders-status" name="status">${options([['ALL', 'Toate statusurile'], ...Object.entries(ORDER_STATUS).map(([key, value]) => [key, value.label])], filters.status)}</select></div>
        <div class="field"><label for="orders-delivery">Tipul de livrare</label><select class="select" id="orders-delivery" name="delivery">${options([['ALL', 'Toate comenzile'], ['PHYSICAL', 'Cu produse fizice'], ['DIGITAL', 'Doar digitale și insigne']], filters.delivery)}</select></div>
        <div class="field"><label for="orders-from">Comandate începând cu</label><input class="input" id="orders-from" name="from" type="date" min="0001-01-01" max="9998-12-31" value="${filters.from}"></div>
        <div class="field"><label for="orders-to">Comandate până la</label><input class="input" id="orders-to" name="to" type="date" min="0001-01-01" max="9998-12-31" value="${filters.to}"></div>
        <div class="field"><label for="orders-sort">Ordinea comenzilor</label><select class="select" id="orders-sort" name="sort">${options([['NEWEST', 'Cele mai recente'], ['OLDEST', 'Cele mai vechi']], filters.sort)}</select></div>
      </div><p class="small muted mt-1">Perioada include ambele zile, după ora României. Comenzile mixte apar la produse fizice.</p></details>
      <div class="row"><button class="btn btn-primary" type="submit">${icon('search')} Aplică filtrele</button><button class="btn btn-ghost" type="button" data-orders-reset>Șterge filtrele</button></div>
    </form>
    <div data-orders-results aria-live="polite"></div>
  </div>`);
  const form = qs('[data-orders-filters]', root);
  const results = qs('[data-orders-results]', root);
  // Câmpurile date native normalizează și valorile invalide primite prin URL.
  filters = formData(form);

  function updateUrl() {
    setQuery({ ...Object.fromEntries(Object.entries(filters).filter(([key, value]) => value !== DEFAULTS[key])), page: page || '' });
  }

  async function load() {
    if (!active()) return;
    const request = ++generation;
    controller?.abort();
    const local = new AbortController();
    controller = local;
    const abort = () => local.abort();
    signal.addEventListener('abort', abort, { once: true });
    results.setAttribute('aria-busy', 'true');
    mount(results, skeletonPage());
    updateUrl();
    try {
      const data = await get('/orders' + queryString({ ...filters, page, size: 10 }), { signal: local.signal });
      if (!active() || request !== generation) return;
      page = data.page;
      filters = data.filters;
      updateUrl();
      const count = Object.keys(DEFAULTS).filter((key) => key !== 'q' && filters[key] !== DEFAULTS[key]).length;
      qs('[data-orders-filter-label]', root).textContent = `Filtre și sortare${count ? ` · ${count} active` : ''}`;
      render(data);
    } catch (err) {
      if (!active() || request !== generation || isAbort(err)) return;
      mount(results, html`<section class="card card-pad stack"><p role="alert">${err.message || 'Comenzile nu au putut fi încărcate.'}</p><div><button class="btn btn-secondary" type="button" data-orders-retry>Reîncearcă</button></div></section>`);
    } finally {
      signal.removeEventListener('abort', abort);
      if (generation === request) results.removeAttribute('aria-busy');
    }
  }

  function render(data) {
    const filtered = Object.keys(DEFAULTS).some((key) => key !== 'sort' && filters[key] !== DEFAULTS[key]);
    mount(results, html`<div class="stack"><p class="small muted" role="status">${plural(data.totalItems, 'comandă găsită', 'comenzi găsite')}</p>
      ${data.items.length ? html`<div class="stack" id="orders-list">${join(data.items, card)}</div>
        <nav class="row-between" aria-label="Paginarea comenzilor"><button class="btn btn-secondary btn-sm" type="button" data-orders-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>Comenzile anterioare</button><span class="small muted">Pagina ${num(page + 1)} din ${num(data.totalPages)}</span><button class="btn btn-secondary btn-sm" type="button" data-orders-page="${page + 1}" ${page + 1 >= data.totalPages ? 'disabled' : ''}>Următoarele comenzi</button></nav>`
      : emptyState(filtered ? { icon: '🔎', title: 'Nicio comandă nu corespunde filtrelor', text: 'Încearcă alt nume de produs, alt status sau o perioadă mai largă.', action: html`<button class="btn btn-secondary" type="button" data-orders-reset>Șterge filtrele</button>` }
        : { icon: '📦', title: 'Nu ai comenzi încă', text: 'Premiile revendicate vor apărea aici.', action: html`<a class="btn btn-primary" href="/magazin">Mergi la magazin</a>` })}
    </div>`);
  }

  function reset() {
    if (!active()) return;
    for (const [key, value] of Object.entries(DEFAULTS)) form.elements.namedItem(key).value = value;
    qs('[data-orders-advanced]', root).open = false;
    filters = { ...DEFAULTS };
    page = 0;
    load();
  }

  const offs = [
    on(root, 'submit', '[data-orders-filters]', (event) => {
      event.preventDefault();
      if (!active()) return;
      filters = formData(form);
      page = 0;
      load();
    }),
    on(root, 'click', '[data-orders-reset]', reset),
    on(root, 'click', '[data-orders-retry]', () => load()),
    on(root, 'click', '[data-orders-page]', (event, button) => { page = Math.max(0, Number(button.dataset.ordersPage)); load(); }),
  ];
  await load();
  return () => { disposed = true; generation++; controller?.abort(); offs.forEach((off) => off()); };
}

function card(order) {
  const href = `/comenzi/${enc(order.id)}`;
  return html`<article class="card order-card" aria-label="Comanda ${order.id}">
    <div class="row-between"><a class="strong mono order-number" href="${href}">${order.id}</a>${orderStatusTag(order.status, order.statusLabel)}</div>
    <div class="row-between small muted"><time datetime="${order.createdAt}">${date(order.createdAt)}</time><span>${plural(order.itemCount, 'produs', 'produse')}${order.requiresShipping ? ' · livrare prin curier' : ' · digital'}</span></div>
    <ul class="order-product-list">${join(order.items.slice(0, 3), (item) => html`<li>${item.quantity > 1 ? `${item.quantity}× ` : ''}${item.available && item.rewardId ? html`<a href="/produs/${enc(item.rewardId)}">${item.name}</a>` : html`<span>${item.name}</span><span class="xsmall muted"> · indisponibil în catalog</span>`}</li>`)}</ul>
    ${order.items.length > 3 ? html`<p class="small muted">Și ${plural(order.items.length - 3, 'alt produs', 'alte produse')} în detaliile comenzii.</p>` : ''}
    <div class="row-between">${apAmount(order.total)}<a class="btn btn-secondary btn-sm" href="${href}" data-order-details aria-label="Vezi comanda ${order.id}">Vezi comanda ${icon('chevronRight')}</a></div>
  </article>`;
}
