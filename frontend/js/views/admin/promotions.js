import { get, post, put, enc, isAbort } from '../../api.js';
import { html, mount, join, qs, on, formData } from '../../dom.js';
import { apAmount, skeletonPage, emptyState } from '../../components.js';
import { dateTime, num } from '../../format.js';
import { promotionInstant, promotionLocalTime, flashPreview, promoPreview } from '../../promotion-math.js';
import { navigate, reload, setQuery } from '../../router.js';
import { busy, toastOk, handleError, copyText } from '../../ui.js';

const OPTIONS = (rows, value) => join(rows, ([key, label]) => html`<option value="${key}" ${key === value ? 'selected' : ''}>${label}</option>`);
const DEAL_STATES = [['', 'Toate'], ['ACTIVE', 'Active'], ['UPCOMING', 'Programate'], ['SOLD_OUT', 'Epuizate'], ['ENDED', 'Încheiate']];
const PROMO_STATES = [['', 'Toate'], ['ACTIVE', 'Active'], ['INACTIVE', 'Inactive'], ['EXPIRED', 'Expirate'], ['SOLD_OUT', 'Utilizări epuizate']];
const stateName = (status) => [...DEAL_STATES, ...PROMO_STATES].find(([key]) => key === status)?.[1] || status;
function status(row, deals) {
  if (!deals && !row.is_active) return 'INACTIVE';
  if (Date.parse(deals ? row.ends_at : row.expires_at) <= Date.now()) return deals ? 'ENDED' : 'EXPIRED';
  if (deals && Date.parse(row.starts_at) > Date.now()) return 'UPCOMING';
  if (Number(deals ? row.sold_quantity : row.current_uses) >= Number(deals ? row.max_quantity : row.max_uses)) return 'SOLD_OUT';
  return 'ACTIVE';
}

export async function adminPromotions(root, { section, query, signal, active, openDialog }) {
  const deals = section === 'deals';
  const base = `/admin/${section}`;
  const endpoint = `/admin/${deals ? 'flash-deals' : 'promo-codes'}`;
  const creating = query.new === '1';
  const editing = !deals && !creating && !!query.id;
  const offs = [];
  mount(root, skeletonPage());
  try {
    if (creating && deals) {
      const rewards = await get('/admin/rewards', { signal });
      if (!active()) return;
      return dealEditor(root, rewards.filter((r) => r.active && r.fulfillment !== 'BADGE'), { base, endpoint, signal, active, openDialog });
    }
    if (!deals && (creating || editing)) {
      const editor = editing ? await get(`${endpoint}/${enc(query.id)}`, { signal }) : null;
      if (!active()) return;
      return promoEditor(root, editor, { base, endpoint, signal, active, openDialog });
    }
    const rows = await get(endpoint, { signal });
    if (!active()) return;
    const states = deals ? DEAL_STATES : PROMO_STATES;
    let filter = { q: String(query.q || '').slice(0, 120), status: states.some(([key]) => key === query.status) ? query.status : '' };
    let page = Math.max(0, Number.parseInt(query.page, 10) || 0);
    function render() {
      if (!active()) return;
      const search = filter.q.trim().toLocaleLowerCase('ro');
      const filtered = rows.filter((row) => (!search || `${row.code || row.reward_name} ${row.description || ''} ${row.id}`.toLocaleLowerCase('ro').includes(search)) && (!filter.status || status(row, deals) === filter.status));
      const pages = Math.max(1, Math.ceil(filtered.length / 20));
      page = Math.min(page, pages - 1);
      setQuery({ q: filter.q, status: filter.status, page: page || undefined });
      mount(root, html`<div class="stack"><div class="row-between"><p class="muted">${deals ? 'Creează oferte acum sau programează-le. Pentru alte condiții, încheie oferta și creează una nouă.' : 'Coduri cu reducere procentuală sau fixă, maximum o utilizare per membru.'}</p><a class="btn btn-primary" href="${base}?new=1">${deals ? 'Ofertă nouă' : 'Cod promo nou'}</a></div>
        ${deals ? html`<p class="small muted">Cel mult 200 de oferte, inclusiv cele încheiate în ultimele 30 de zile. Cantitatea ofertei nu rezervă stocul produsului.</p>` : ''}
        <form class="card card-pad catalog-filter-grid" data-promotion-filter role="search" aria-label="Filtrează promoțiile"><div class="field"><label for="promotion-search">${deals ? 'Caută oferte' : 'Caută coduri'}</label><input class="input" id="promotion-search" name="q" type="search" value="${filter.q}" maxlength="120"></div><div class="field"><label for="promotion-state">Stare promoție</label><select class="select" id="promotion-state" name="status">${OPTIONS(deals ? DEAL_STATES : PROMO_STATES, filter.status)}</select></div><button class="btn btn-secondary" type="submit">Filtrează</button></form>
        <p class="small muted" role="status">${num(filtered.length)} rezultate</p>
        ${filtered.length ? html`<div class="admin-card-list">${join(filtered.slice(page * 20, (page + 1) * 20), (row) => {
          const current = status(row, deals);
          return html`<article class="card admin-item promotion-item"><div class="admin-item-head"><h2>${deals ? row.reward_name : row.code}</h2><span class="tag ${current === 'ACTIVE' ? 'tag-success' : ''}">${stateName(current)}</span></div>
            ${deals ? html`<p>−${row.discount_percent}% · ${apAmount(flashPreview(row.price, row.discount_percent))} <span class="muted">în loc de ${num(row.price)} AP</span></p><p class="small muted">${dateTime(row.starts_at)} → ${dateTime(row.ends_at)} · ${row.auto_generated ? 'Automată' : 'Manuală'}</p><p>${num(row.sold_quantity)}/${num(row.max_quantity)} bucăți · maximum ${num(row.per_user_limit)}/membru</p>
              ${current !== 'ENDED' ? html`<div><button class="btn btn-secondary btn-sm" type="button" data-end-deal="${row.id}">${current === 'UPCOMING' ? 'Anulează programarea' : 'Încheie oferta'}</button></div>` : ''}`
            : html`<p>${row.description}</p><p>−${row.discount_percent > 0 ? html`${row.discount_percent}%${row.max_discount ? html` · plafon ${num(row.max_discount)} AP` : ''}` : html`${num(row.discount_points)} AP`} · comandă minimă ${num(row.min_order_points)} AP</p><p class="small muted">${num(row.current_uses)}/${num(row.max_uses)} utilizări · ${num(row.total_discount)} AP acordate · ${row.expires_at ? html`expiră ${dateTime(row.expires_at)}` : 'fără expirare'}</p><div class="row wrap"><a class="btn btn-secondary btn-sm" href="${base}?id=${enc(row.id)}" aria-label="Editează ${row.code}">Editează</a><button class="btn btn-ghost btn-sm" type="button" data-copy-promo="${row.code}">Copiază codul</button></div>`}</article>`;
        })}</div>` : emptyState({ title: 'Nicio promoție găsită', text: 'Schimbă căutarea sau filtrul.' })}
        ${pages > 1 ? html`<nav class="row-between" aria-label="Paginarea promoțiilor"><button class="btn btn-secondary" type="button" data-promotion-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>Anterior</button><span>${page + 1}/${pages}</span><button class="btn btn-secondary" type="button" data-promotion-page="${page + 1}" ${page + 1 >= pages ? 'disabled' : ''}>Următor</button></nav>` : ''}</div>`);
    }
    offs.push(on(root, 'submit', '[data-promotion-filter]', (event, form) => { event.preventDefault(); filter = formData(form); page = 0; render(); }));
    offs.push(on(root, 'click', '[data-promotion-page]', (event, button) => { page = Math.max(0, Number(button.dataset.promotionPage)); render(); }));
    offs.push(on(root, 'click', '[data-copy-promo]', (event, button) => { if (active()) copyText(button.dataset.copyPromo); }));
    offs.push(on(root, 'click', '[data-end-deal]', (event, button) => {
      if (!active()) return;
      busy(button, async () => {
        const row = rows.find((item) => item.id === button.dataset.endDeal);
        if (!row) return;
        const upcoming = status(row, true) === 'UPCOMING';
        const confirmed = await openDialog({ title: upcoming ? 'Anulezi oferta programată?' : 'Închei oferta acum?', content: html`<p>${row.reward_name} · −${row.discount_percent}%. ${upcoming ? 'Programarea va fi eliminată.' : 'Comenzile deja plasate își păstrează prețul.'}</p>`,
          footer: html`<button class="btn btn-ghost" type="button" data-close>Renunță</button><button class="btn btn-primary" type="button" data-confirm-promotion>Confirmă</button>`, onMount: (dlg, close) => qs('[data-confirm-promotion]', dlg).addEventListener('click', () => close(true)) });
        if (confirmed !== true || !active()) return;
        try {
          const result = await post(`${endpoint}/${enc(row.id)}/end`, {}, { signal });
          if (!active()) return;
          toastOk(result.message); reload();
        } catch (error) { if (active() && !isAbort(error)) handleError(error); }
      });
    }));
    render();
    return () => offs.forEach((off) => off());
  } catch (error) {
    if (active() && !isAbort(error)) mount(root, html`<p class="callout is-warning" role="alert">${error.message}</p>`);
  }
}

function field(name, label, value, attributes = html``) {
  return html`<div class="field"><label for="promotion-${name}">${label}</label><input class="input" id="promotion-${name}" name="${name}" value="${value}" ${attributes}></div>`;
}
const integer = (min, max) => html`type="number" required min="${min}" max="${max}" step="1"`;
const datesHint = html`<p class="small muted">Toate datele și orele sunt în fusul Europe/Bucharest (ora României). Orele ambigue de la schimbarea orei sunt respinse.</p>`;
const actions = (creating, noun) => html`<div data-promotion-error></div><div class="row wrap"><button class="btn btn-primary" type="submit">${creating ? 'Creează' : 'Salvează'} ${noun}</button><button class="btn btn-ghost" type="button" data-promotion-reload>${creating ? 'Resetează formularul' : 'Încarcă codul actual'}</button></div>`;

function dealEditor(root, rewards, context) {
  const { base } = context;
  if (!rewards.length) { mount(root, emptyState({ title: 'Niciun produs eligibil', text: 'Activează mai întâi un produs în catalog.', action: html`<a class="btn btn-primary" href="/admin/rewards">Deschide catalogul</a>` })); return; }
  mount(root, html`<div class="stack"><a href="${base}">Înapoi la oferte</a><h2>Ofertă nouă</h2><div class="catalog-editor-layout"><form class="card card-pad stack" data-promotion-editor>
    <div class="field"><label for="promotion-reward">Produs</label><select class="select" id="promotion-reward" name="rewardId">${OPTIONS(rewards.map((r) => [r.id, `${r.name} · stoc ${num(r.stock_count)}`]), rewards[0].id)}</select></div>
    <div class="catalog-field-grid">${field('discountPercent', 'Reducere procentuală', 20, integer(5, 80))}${field('maxQuantity', 'Cantitate în ofertă', 10, integer(1, 100000))}${field('perUserLimit', 'Limită per membru', 2, integer(1, 99))}</div>
    <label class="check"><input type="checkbox" name="immediate" checked> Începe imediat</label>
    <div class="catalog-field-grid">${field('startsAt', 'Începe la', '', html`type="datetime-local" disabled`)}${field('endsAt', 'Se încheie la', '', html`type="datetime-local" required`)}</div>${datesHint}${actions(true, 'oferta')}
    </form><aside class="card card-pad stack catalog-preview" aria-label="Previzualizarea ofertei"><h3>Previzualizare ofertă</h3><div data-promotion-preview></div><p class="small muted">Preț per bucată înaintea reducerii de nivel și a codului promo. Oferta nu rezervă stoc; checkout-ul verifică disponibilitatea și limitele.</p></aside></div></div>`);
  const form = qs('[data-promotion-editor]', root);
  function body() {
    const f = formData(form);
    return { rewardId: f.rewardId, discountPercent: Number(f.discountPercent), maxQuantity: Number(f.maxQuantity), perUserLimit: Number(f.perUserLimit),
      startsAt: form.elements.immediate.checked ? null : promotionInstant(f.startsAt), endsAt: promotionInstant(f.endsAt) };
  }
  function preview() {
    const f = formData(form), reward = rewards.find((r) => r.id === f.rewardId);
    const immediate = form.elements.immediate.checked;
    form.elements.startsAt.disabled = immediate; form.elements.startsAt.required = !immediate;
    const percent = Number(f.discountPercent);
    mount(qs('[data-promotion-preview]', root), html`<div class="stack"><strong>${reward.name}</strong><span class="tag tag-brand">−${num(percent)}%</span>${apAmount(flashPreview(reward.price, percent))}<p class="muted">Preț de catalog: ${num(reward.price)} AP · stoc la citire: ${num(reward.stock_count)}</p><p>${immediate ? 'Pornește la salvare' : 'Programată'} · maximum ${num(f.maxQuantity)} bucăți · ${num(f.perUserLimit)}/membru</p>${reward.stock_count === 0 ? html`<p class="callout is-warning">Produsul nu are stoc acum. Oferta devine cumpărabilă numai după reaprovizionare.</p>` : ''}</div>`);
  }
  return editorFlow(root, { ...context, form, body, preview, creating: true, success: () => navigate(base) });
}

function promoEditor(root, editor, context) {
  const creating = !editor;
  const p = editor?.promo || { code: '', description: '', discountPercent: 15, discountPoints: 0, maxDiscount: null, minOrderPoints: 0, maxUses: 100, currentUses: 0, expiresAt: null, active: false };
  const initialExpiry = promotionLocalTime(p.expiresAt);
  mount(root, html`<div class="stack"><a href="${context.base}">Înapoi la coduri promo</a><h2>${creating ? 'Cod promo nou' : p.code}</h2><div class="catalog-editor-layout"><form class="card card-pad stack" data-promotion-editor>
    ${field('code', 'Cod promo', p.code, html`required minlength="3" maxlength="32" pattern="(?:[A-Za-z0-9_]|-){3,32}" autocomplete="off" ${creating ? '' : 'readonly'}`)}
    ${field('description', 'Descriere internă', p.description, html`maxlength="140"`)}
    <div class="field"><label for="promotion-type">Tip reducere</label><select class="select" name="type" id="promotion-type">${OPTIONS([['percent', 'Procentuală'], ['points', 'Sumă fixă în AP']], p.discountPercent > 0 ? 'percent' : 'points')}</select></div>
    <div class="catalog-field-grid">${field('discount', 'Valoarea reducerii', p.discountPercent || p.discountPoints, integer(1, p.discountPercent > 0 ? 90 : 1000000))}${field('maxDiscount', 'Plafon reducere în AP (0 = fără)', p.maxDiscount || 0, integer(0, 1000000))}${field('minOrderPoints', 'Comandă minimă în AP', p.minOrderPoints, integer(0, 1000000))}${field('maxUses', 'Număr maxim de utilizări', p.maxUses, integer(Math.max(1, p.currentUses), 1000000))}</div>
    <p class="small muted">${num(p.currentUses)} utilizări deja înregistrate. Fiecare membru poate folosi codul o singură dată. Plafonul se aplică reducerilor procentuale.</p>
    <label class="check"><input type="checkbox" name="hasExpiry" ${p.expiresAt ? 'checked' : ''}> Are dată de expirare</label>${field('expiresAt', 'Expiră la', initialExpiry, html`type="datetime-local" ${p.expiresAt ? 'required' : 'disabled'}`)}${datesHint}
    <label class="check"><input type="checkbox" name="active" ${p.active ? 'checked' : ''}> Cod activ</label>${actions(creating, 'codul')}
    </form><aside class="card card-pad stack catalog-preview" aria-label="Previzualizarea codului promo"><h3>Simulează reducerea</h3>${field('sampleAmount', 'Sumă de test în AP', 1000, integer(0, 1980000000))}<p class="small muted">Suma rămasă după ofertele flash și reducerea de nivel.</p><div data-promotion-preview></div><p class="small muted">Simulare pentru o comandă eligibilă. Disponibilitatea codului, expirarea și utilizările se verifică la checkout. Simularea nu consumă codul.</p></aside></div></div>`);
  const form = qs('[data-promotion-editor]', root);
  function values() {
    const f = formData(form), percent = f.type === 'percent';
    return { code: f.code.trim().toUpperCase(), description: f.description, discountPercent: percent ? Number(f.discount) : 0, discountPoints: percent ? 0 : Number(f.discount), maxDiscount: percent ? Number(f.maxDiscount) : 0,
      minOrderPoints: Number(f.minOrderPoints), maxUses: Number(f.maxUses), active: form.elements.active.checked };
  }
  function body() {
    const f = formData(form), expiry = form.elements.hasExpiry.checked;
    // Păstrăm precizia și orele istorice ambigue dacă administratorul nu schimbă data existentă.
    return { ...values(), ...(creating ? {} : { version: editor.version }), clearExpiry: !expiry,
      ...(expiry ? { expiresAt: p.expiresAt && f.expiresAt === initialExpiry ? p.expiresAt : promotionInstant(f.expiresAt) } : {}) };
  }
  function preview() {
    const value = values(), percent = form.elements.type.value === 'percent';
    form.elements.discount.max = percent ? '90' : '1000000';
    form.elements.maxDiscount.disabled = !percent;
    form.elements.expiresAt.disabled = !form.elements.hasExpiry.checked;
    form.elements.expiresAt.required = form.elements.hasExpiry.checked;
    const result = promoPreview(Number(qs('[name="sampleAmount"]', root).value), value);
    mount(qs('[data-promotion-preview]', root), html`<div class="stack"><strong>${value.code || 'CODUL-TĂU'}</strong><span class="tag ${value.active ? 'tag-success' : ''}">${value.active ? 'Activ după salvare' : 'Inactiv după salvare'}</span>${result ? html`<p>Reducere simulată: <strong>${num(result.discount)} AP</strong></p><p>Total simulat: ${apAmount(result.total)}</p>${result.minimum ? html`<p class="callout is-warning">Comanda minimă este ${num(value.minOrderPoints)} AP.</p>` : ''}` : html`<p>Alege o sumă între 0 și 1.980.000.000 AP.</p>`}</div>`);
  }
  return editorFlow(root, { ...context, form, body, preview, creating, id: p.id, success: (result) => navigate(`${context.base}?id=${enc(result.id)}`, { replace: true }) });
}

/** Formularul rămâne doar în pagină; schimbarea sesiunii îl elimină prin părintele admin. */
function editorFlow(root, { form, body, preview, creating, id, endpoint, signal, active, openDialog, success }) {
  let dirty = false, conflicted = false;
  const save = qs('[type="submit"]', form), offs = [];
  async function discard() {
    if (!dirty) return true;
    return await openDialog({ title: 'Renunți la modificările nesalvate?', content: html`<p>Formularul va fi înlocuit. Modificările nesalvate se pierd.</p>`, footer: html`<button class="btn btn-ghost" type="button" data-close>Continuă editarea</button><button class="btn btn-primary" type="button" data-discard-promotion>Renunță la modificări</button>`, onMount: (dlg, close) => qs('[data-discard-promotion]', dlg).addEventListener('click', () => close(true)) }) === true && active();
  }
  const beforeUnload = (event) => { if (dirty && active()) { event.preventDefault(); event.returnValue = ''; } };
  const beforeLink = (event) => {
    if (!dirty || !active() || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
    const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
    if (!link || link.target || link.hasAttribute('download') || link.getAttribute('href').startsWith('#')) return;
    const url = new URL(link.href, location.href);
    if (url.origin !== location.origin) return;
    event.preventDefault(); event.stopImmediatePropagation();
    discard().then((confirmed) => { if (confirmed && active()) { dirty = false; navigate(url.pathname + url.search + url.hash); } });
  };
  window.addEventListener('beforeunload', beforeUnload);
  document.addEventListener('click', beforeLink, true);
  const change = (event) => { if (!active()) return; if (form.contains(event.target)) dirty = true; preview(); };
  root.addEventListener('input', change); root.addEventListener('change', change);
  offs.push(on(root, 'click', '[data-promotion-reload]', async () => { if (active() && await discard()) { dirty = false; reload(); } }));
  offs.push(on(root, 'submit', '[data-promotion-editor]', (event) => {
    event.preventDefault();
    if (!active() || conflicted) return;
    busy(save, async () => {
      try {
        const value = body();
        const result = creating ? await post(endpoint, value, { signal }) : await put(`${endpoint}/${enc(id)}`, value, { signal });
        if (!active()) return;
        dirty = false; toastOk('Promoția a fost salvată.'); success(result);
      } catch (error) {
        if (!active() || isAbort(error)) return;
        conflicted = ['PROMO_CHANGED', 'PROMO_VERSION_REQUIRED'].includes(error.code);
        mount(qs('[data-promotion-error]', root), html`<p class="callout is-warning" role="alert">${error.message}${conflicted ? ' Ciorna ta este încă în formular. Încarcă codul actual și verifică modificările.' : ''}</p>`);
      }
    }).then(() => { if (active()) save.disabled = conflicted; });
  }));
  preview();
  return () => { offs.forEach((off) => off()); root.removeEventListener('input', change); root.removeEventListener('change', change); window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('click', beforeLink, true); };
}
