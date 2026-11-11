import { get, post, enc, query as searchParams } from '../../api.js';
import { apAmount, emptyState, orderStatusTag, ORDER_STATUS, skeletonPage } from '../../components.js';
import { formData, html, join, mount, on } from '../../dom.js';
import { dateTime, num } from '../../format.js';
import { icon } from '../../icons.js';
import { navigate, reload } from '../../router.js';
import { setMeta } from '../../seo.js';
import { busy, confirmDialog, handleError, toastOk } from '../../ui.js';

const SECTIONS = {
  stats: ['Prezentare', 'grid'], orders: ['Comenzi', 'package'], rewards: ['Stoc', 'bag'],
  users: ['Membri', 'users'], audit: ['Jurnal', 'history'],
};

const adminPath = (section, params) => `/admin${section === 'stats' ? '' : `/${section}`}${searchParams(params)}`;
const adminApi = (section, id) => `/admin/${section}${id ? `/${enc(id)}` : ''}`;

export default async function admin({ root, params, query, signal, alive }) {
  const section = params.section || 'stats';
  if (!Object.hasOwn(SECTIONS, section)) throw new Error('Secțiunea de administrare nu există.');
  const id = ['orders', 'users'].includes(section) ? query.id : undefined;
  const page = Math.max(0, Number.parseInt(query.page, 10) || 0);
  const title = SECTIONS[section][0];
  setMeta({ title: `Administrare · ${title}`, noindex: true });
  mount(root, skeletonPage());
  const data = await get(`${adminApi(section, id)}${searchParams(id ? null : { q: query.q, status: query.status, page })}`, { signal });
  if (!alive()) return;

  const content = section === 'stats' ? stats(data)
    : id ? (section === 'orders' ? order(data) : user(data))
    : listing(section, data, query, page);

  mount(root, html`<div class="admin">
    <nav class="admin-nav" aria-label="Administrare">${join(Object.entries(SECTIONS), ([key, [label, ico]]) => html`
      <a href="${adminPath(key)}" ${key === section ? html`aria-current="page"` : ''}>${icon(ico)} ${label}</a>`)}</nav>
    <section class="admin-main"><h1>${title}</h1>${content}</section>
  </div>`);

  const unbindFilter = on(root, 'submit', '[data-filter]', (event, form) => {
    event.preventDefault();
    navigate(adminPath(section, formData(form)));
  });
  const unbindAction = on(root, 'submit', '[data-mutation]', (event, form) => {
    event.preventDefault();
    const button = event.submitter || form.querySelector('[type="submit"]');
    busy(button, async () => {
      try {
        const body = formData(form);
        for (const key of ['amount', 'delta']) if (key in body) body[key] = Number(body[key]);
        if ('banned' in body) body.banned = body.banned === 'true';
        if (form.dataset.confirm && !await confirmDialog({ title: form.dataset.confirm, body: 'Acțiunea va fi consemnată în jurnal.' })) return;
        if (!alive()) return;
        const result = await post(form.dataset.mutation, body);
        if (!alive()) return;
        toastOk(result.message || 'Modificarea a fost salvată.');
        reload();
      } catch (error) {
        if (alive()) handleError(error);
      }
    });
  });
  return () => { unbindFilter(); unbindAction(); };
}

function stats(data) {
  const figures = [
    ['Membri', data.users.total, 'users'], ['Comenzi de pregătit', data.orders.toFulfill, 'package'],
    ['Produse fără stoc', data.catalog.outOfStock, 'bag'], ['Jucători Wordle astăzi', data.community.wordlePlayersToday, 'play'],
  ];
  return html`<div class="kpis">${join(figures, ([label, value, ico]) => html`
    <div class="card card-pad stack-sm">${icon(ico)} <strong>${num(value)}</strong><span class="muted">${label}</span></div>`)}</div>
    <form class="card card-pad stack" data-mutation="/admin/vouchers/redeem" data-confirm="Marchezi voucherul ca folosit?">
      <h2>Valorifică un voucher</h2>
      <label class="field">Cod voucher <input class="input" name="code" autocomplete="off" required maxlength="40"></label>
      <div><button class="btn btn-primary" type="submit">Valorifică</button></div>
    </form>`;
}

function listing(section, data, query, page) {
  const items = Array.isArray(data) ? data : data.items;
  return html`
    ${['orders', 'users'].includes(section) ? html`<form class="card card-pad form-grid" data-filter>
      <label class="field">Caută <input class="input" name="q" type="search" value="${query.q}" maxlength="120"></label>
      ${section === 'orders' ? html`<label class="field">Status <select class="select" name="status"><option value="">Toate</option>
        ${join(Object.entries(data.statuses), ([key, label]) => option(key, label, key === query.status))}</select></label>` : ''}
      <div class="span-2"><button class="btn btn-secondary" type="submit">Filtrează</button></div>
    </form>` : ''}
    ${items.length ? html`<div class="admin-card-list">${join(items, (row) => item(section, row))}</div>` : emptyState({ title: 'Niciun rezultat' })}
    <div class="row wrap">${page > 0 ? html`<a class="btn btn-secondary" href="${adminPath(section, { ...query, page: page - 1 })}">Pagina anterioară</a>` : ''}
      ${data.hasMore ? html`<a class="btn btn-secondary" href="${adminPath(section, { ...query, page: page + 1 })}">Pagina următoare</a>` : ''}</div>`;
}

const option = (value, label, selected) => html`<option value="${value}" ${selected ? html`selected` : ''}>${label}</option>`;
const reason = (required = true) => html`<label class="field">Motiv <input class="input" name="reason" minlength="3" maxlength="200" ${required ? html`required` : ''}></label>`;

function item(section, row) {
  if (section === 'orders') return html`<article class="card admin-item"><div class="admin-item-head">
    <a class="strong" href="${adminPath(section, { id: row.id })}">${row.id}</a>${orderStatusTag(row.status)}</div>
    <div class="kv">${row.user_name} · ${dateTime(row.created_at)} · ${apAmount(row.total)}</div></article>`;
  if (section === 'users') return html`<article class="card admin-item"><a class="strong" href="${adminPath(section, { id: row.id })}">${row.name}</a>
    <div class="kv">${row.email} · ${row.role} · ${row.banned ? 'Suspendat' : 'Activ'} · ${apAmount(row.activity_points)}</div></article>`;
  if (section === 'rewards') return html`<article class="card admin-item"><h2 class="h4">${row.name}</h2><p class="muted">Stoc ${num(row.stock_count)} · ${row.active ? 'Activ' : 'Inactiv'}</p>
    <form class="form-grid" data-mutation="${adminApi(section, row.id)}/stock">
      <label class="field">Diferență stoc <input class="input" type="number" name="delta" required step="1"></label>${reason()}
      <div class="span-2"><button class="btn btn-secondary" type="submit">Actualizează stocul</button></div>
    </form></article>`;
  return html`<article class="card admin-item"><strong>${row.action}</strong><div class="kv">${row.admin_name || row.admin_id} · ${dateTime(row.created_at)} · ${row.target_type} ${row.target_id}</div><p>${row.details}</p></article>`;
}

function order(data) {
  const o = data.order;
  const shipping = o.shipping;
  return html`<a href="/admin/orders">Înapoi la comenzi</a>
    <article class="card card-pad stack"><div class="admin-item-head"><h2>${o.id}</h2>${orderStatusTag(o.status, o.statusLabel)}</div>
      <div>${data.customer?.name} ${data.customer?.email} · ${apAmount(o.total)}</div>
      ${shipping ? html`<div>${shipping.name} · ${shipping.phone}<br>${shipping.address}, ${shipping.city}, ${shipping.county} ${shipping.postalCode}</div>` : ''}
      ${o.trackingCode ? html`<div>AWB ${o.trackingCode}</div>` : ''}
      ${o.notes ? html`<p>${o.notes}</p>` : ''}
      <ul>${join(o.items, (line) => html`<li>${line.quantity} × ${line.name}</li>`)}</ul>
    </article>
    ${data.allowedTransitions.length ? html`<form class="card card-pad form-grid" data-mutation="${adminApi('orders', o.id)}/status">
      <label class="field">Status <select class="select" name="status">${join(data.allowedTransitions, (status) => option(status, ORDER_STATUS[status]?.label || status))}</select></label>
      <label class="field">AWB <input class="input" name="trackingCode" maxlength="64" value="${o.trackingCode}"></label>
      <label class="field span-2">Notă <input class="input" name="note" maxlength="255"></label>
      <div class="span-2"><button class="btn btn-primary" type="submit">Actualizează comanda</button></div>
    </form>` : ''}
    ${data.canCancel ? html`<form class="card card-pad stack" data-mutation="${adminApi('orders', o.id)}/cancel" data-confirm="Anulezi comanda și returnezi punctele?">
      ${reason()}<div><button class="btn btn-danger" type="submit">Anulează comanda</button></div></form>` : ''}`;
}

function user(data) {
  const u = data.user;
  const endpoint = adminApi('users', u.id);
  return html`<a href="/admin/users">Înapoi la membri</a>
    <article class="card card-pad stack"><h2>${u.name}</h2><p>${u.email} · ${u.role} · ${apAmount(u.activityPoints)}</p></article>
    <form class="card card-pad form-grid" data-mutation="${endpoint}/points" data-confirm="Modifici soldul acestui membru?">
      <label class="field">Diferență AP <input class="input" type="number" name="amount" required min="-1000000" max="1000000" step="1"></label>${reason()}
      <div class="span-2"><button class="btn btn-primary" type="submit">Ajustează soldul</button></div></form>
    ${u.role !== 'ADMIN' ? html`<form class="card card-pad stack" data-mutation="${endpoint}/ban" data-confirm="Schimbi accesul la acest cont?">
      <input type="hidden" name="banned" value="${!data.banned ? 'true' : 'false'}">${reason(!data.banned)}
      <div><button class="btn ${data.banned ? 'btn-secondary' : 'btn-danger'}" type="submit">${data.banned ? 'Reactivează' : 'Suspendă contul'}</button></div></form>` : ''}`;
}
