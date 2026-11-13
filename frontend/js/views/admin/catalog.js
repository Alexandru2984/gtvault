import { get, post, put, enc, isAbort } from '../../api.js';
import { html, mount, join, qs, on, formData, imgSrc, PLACEHOLDER_IMG } from '../../dom.js';
import { apAmount, skeletonPage, emptyState, FULFILLMENT } from '../../components.js';
import { num, plural } from '../../format.js';
import { navigate, reload, setQuery } from '../../router.js';
import { busy, toastOk } from '../../ui.js';

const IMAGES = [
  ['images/gt-logo.svg', 'Sigla RewardHub'], ['images/products/agenda.svg', 'Agendă'],
  ['images/products/badge.svg', 'Insignă'], ['images/products/chatgpt.svg', 'Ghid AI'],
  ['images/products/cv.svg', 'CV'], ['images/products/lucky.svg', 'Cadou'],
  ['images/products/meet.svg', 'Întâlnire'], ['images/products/mentorat.svg', 'Mentorat'],
  ['images/products/mouse.svg', 'Mouse'], ['images/products/sticker.svg', 'Sticker'],
  ['images/products/tracks.svg', 'Trasee'], ['images/products/tricou.svg', 'Tricou'], ['images/products/udemy.svg', 'Curs'],
];
const TIERS = ['', 'BRONZE', 'SILVER', 'GOLD', 'PLATINUM', 'DIAMOND'];
const base = '/admin/rewards';
const endpoint = '/admin/rewards';
const options = (list, value) => join(list, ([key, label]) => html`<option value="${key}" ${key === value ? 'selected' : ''}>${label}</option>`);

export async function adminCatalog(root, { query, signal, active, openDialog }) {
  mount(root, skeletonPage());
  const creating = query.new === '1';
  const editing = !creating && !!query.id;
  let rows, editor;
  let dirty = false;
  let conflicted = false;
  try {
    [rows, editor] = await Promise.all([get(endpoint, { signal }), editing ? get(`${endpoint}/${enc(query.id)}`, { signal }) : null]);
  } catch (err) {
    if (active() && !isAbort(err)) mount(root, html`<div class="card card-pad stack"><p role="alert">${err.message}</p><a href="${base}">Înapoi la catalog</a></div>`);
    return;
  }
  if (!active()) return;
  const offs = [];
  const imageFallback = () => root.querySelectorAll('img').forEach((img) => img.addEventListener('error', () => {
    if (!img.src.endsWith(PLACEHOLDER_IMG)) img.src = PLACEHOLDER_IMG;
  }, { once: true }));

  if (!creating && !editing) {
    const f = { q: String(query.q || '').slice(0, 80), visibility: ['ACTIVE', 'INACTIVE', 'EMPTY'].includes(query.visibility) ? query.visibility : 'ALL', sort: ['PRICE_ASC', 'PRICE_DESC', 'STOCK'].includes(query.sort) ? query.sort : 'NAME' };
    mount(root, html`<div class="stack"><div class="row-between"><p class="muted">Produse, vizibilitate și stoc, cu modificări consemnate în jurnal.</p><a class="btn btn-primary" href="${base}?new=1">Produs nou</a></div>
      <form class="card card-pad catalog-filter-grid" data-catalog-filter role="search" aria-label="Filtrează catalogul administrativ">
        <div class="field"><label for="catalog-search">Caută produse</label><input class="input" id="catalog-search" type="search" name="q" value="${f.q}" maxlength="80" autocomplete="off"></div>
        <div class="field"><label for="catalog-visibility">Vizibilitate</label><select class="select" id="catalog-visibility" name="visibility">${options([['ALL', 'Toate produsele'], ['ACTIVE', 'Active'], ['INACTIVE', 'Inactive'], ['EMPTY', 'Active fără stoc']], f.visibility)}</select></div>
        <div class="field"><label for="catalog-sort">Sortarea catalogului</label><select class="select" id="catalog-sort" name="sort">${options([['NAME', 'Nume'], ['PRICE_ASC', 'Preț crescător'], ['PRICE_DESC', 'Preț descrescător'], ['STOCK', 'Stoc crescător']], f.sort)}</select></div>
        <button class="btn btn-secondary" type="submit">Filtrează</button>
      </form><div data-catalog-results></div></div>`);
    let page = Math.max(0, Number.parseInt(query.page, 10) || 0);
    function renderList() {
      if (!active()) return;
      const needle = f.q.toLocaleLowerCase('ro-RO');
      const found = rows.filter((row) => `${row.name} ${row.category} ${row.id}`.toLocaleLowerCase('ro-RO').includes(needle)
        && (f.visibility === 'ALL' || f.visibility === 'ACTIVE' && row.active || f.visibility === 'INACTIVE' && !row.active || f.visibility === 'EMPTY' && row.active && row.stock_count === 0));
      found.sort((a, b) => (f.sort === 'PRICE_ASC' ? a.price - b.price : f.sort === 'PRICE_DESC' ? b.price - a.price : f.sort === 'STOCK' ? a.stock_count - b.stock_count : a.name.localeCompare(b.name, 'ro')) || a.id.localeCompare(b.id));
      const pages = Math.ceil(found.length / 20);
      page = Math.min(page, Math.max(0, pages - 1));
      setQuery({ q: f.q, visibility: f.visibility === 'ALL' ? '' : f.visibility, sort: f.sort === 'NAME' ? '' : f.sort, page: page || '' });
      mount(qs('[data-catalog-results]', root), html`<div class="stack"><p class="small muted" role="status">${plural(found.length, 'produs găsit', 'produse găsite')}</p>
        ${found.length ? html`<div class="admin-card-list">${join(found.slice(page * 20, page * 20 + 20), (row) => html`<article class="card admin-catalog-item">
          <img src="${imgSrc(row.image)}" alt="" width="80" height="80" loading="lazy"><div class="stack stack-sm"><div class="row-between"><h2 class="h4">${row.name}</h2><span class="tag ${row.active ? 'tag-success' : ''}">${row.active ? 'Activ' : 'Inactiv'}</span></div>
          <p class="small muted">${row.category} · ${FULFILLMENT[row.fulfillment]?.label || row.fulfillment}</p><div class="row-between">${apAmount(row.price)}<span class="small">Stoc: ${num(row.stock_count)} · limita ${row.max_per_order}/comandă</span></div>
          <div class="row-between small"><span class="muted">${num(row.sold_last_30_days)} vândute în 30 zile · ${num(row.wishlist_count)} favorite</span><a class="btn btn-secondary btn-sm" href="${base}?id=${enc(row.id)}" aria-label="Editează ${row.name}">Editează</a></div></div>
        </article>`)}</div>` : emptyState({ title: 'Niciun produs găsit', text: 'Schimbă numele sau filtrul de vizibilitate.' })}
        ${pages > 1 ? html`<nav class="row-between" aria-label="Paginarea catalogului"><button class="btn btn-secondary" type="button" data-catalog-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>Anterior</button><span>${page + 1}/${pages}</span><button class="btn btn-secondary" type="button" data-catalog-page="${page + 1}" ${page + 1 >= pages ? 'disabled' : ''}>Următor</button></nav>` : ''}</div>`);
      imageFallback();
    }
    offs.push(on(root, 'submit', '[data-catalog-filter]', (event, form) => { event.preventDefault(); Object.assign(f, formData(form)); page = 0; renderList(); }));
    offs.push(on(root, 'click', '[data-catalog-page]', (event, button) => { page = Math.max(0, Number(button.dataset.catalogPage)); renderList(); }));
    renderList();
    return () => offs.forEach((off) => off());
  }

  const product = editing ? editor.product : { name: '', description: '', fullDescription: '', price: 500, image: 'images/gt-logo.svg', category: '', stockCount: 0, active: false, new: false, fulfillment: 'DIGITAL', maxPerOrder: 5, minTier: '' };
  const badge = product.fulfillment === 'BADGE';
  const categories = [...new Set(rows.map((row) => row.category))].sort();
  const pictures = [...IMAGES];
  if (product.image && !pictures.some(([path]) => path === product.image)) pictures.unshift([product.image, 'Imagine existentă']);
  mount(root, html`<div class="stack"><a href="${base}">Înapoi la catalog</a><h2>${creating ? 'Produs nou' : product.name}</h2>
    <div class="catalog-editor-layout"><div class="stack"><form class="card card-pad stack" data-product-editor>
      <h3>Datele produsului</h3><div class="catalog-field-grid">
        <div class="field span-2"><label for="catalog-name">Nume produs</label><input class="input" id="catalog-name" name="name" value="${product.name}" required minlength="2" maxlength="80"></div>
        <div class="field"><label for="catalog-price">Preț în AP</label><input class="input" id="catalog-price" name="price" type="number" value="${product.price}" required min="1" max="1000000" step="1"></div>
        <div class="field"><label for="catalog-category">Categoria</label><input class="input" id="catalog-category" name="category" value="${product.category}" list="catalog-categories" required minlength="2" maxlength="40"><datalist id="catalog-categories">${join(categories, (value) => html`<option value="${value}"></option>`)}</datalist></div>
        <div class="field span-2"><label for="catalog-description">Descriere scurtă</label><textarea class="textarea" id="catalog-description" name="description" rows="3" required minlength="5" maxlength="500">${product.description}</textarea></div>
        <div class="field span-2"><label for="catalog-full-description">Descriere completă</label><textarea class="textarea" id="catalog-full-description" name="fullDescription" rows="5" required minlength="5" maxlength="2000">${product.fullDescription}</textarea></div>
        <div class="field span-2"><label for="catalog-image">Imagine din bibliotecă</label><select class="select" id="catalog-image" name="image" aria-describedby="catalog-image-hint">${options(pictures, product.image || 'images/gt-logo.svg')}</select><span class="hint" id="catalog-image-hint">Imagini locale din biblioteca site-ului.</span></div>
        <div class="field"><label for="catalog-fulfillment">Tipul de livrare</label><select class="select" id="catalog-fulfillment" name="fulfillment" ${badge ? 'disabled' : ''}>${options(badge ? [['BADGE', 'Insignă Hero']] : [['DIGITAL', 'Digital · voucher'], ['PHYSICAL', 'Livrare fizică']], product.fulfillment)}</select></div>
        <div class="field"><label for="catalog-maximum">Limită per comandă</label><input class="input" id="catalog-maximum" name="maxPerOrder" type="number" value="${product.maxPerOrder}" required min="1" max="99" step="1" ${badge ? 'readonly' : ''}></div>
        <div class="field"><label for="catalog-tier">Nivel minim</label><select class="select" id="catalog-tier" name="minTier">${options(TIERS.map((value) => [value, value || 'Orice nivel']), product.minTier || '')}</select></div>
        ${creating ? html`<div class="field"><label for="catalog-initial-stock">Stoc inițial</label><input class="input" id="catalog-initial-stock" name="stockCount" type="number" value="0" required min="0" max="1000000" step="1"></div>` : ''}
      </div><label class="check"><input type="checkbox" name="active" ${product.active ? 'checked' : ''}> Produs activ în magazin</label><label class="check"><input type="checkbox" name="isNew" ${product.new ? 'checked' : ''}> Marchează ca nou</label>
      <div data-editor-error></div><div class="row"><button class="btn btn-primary" type="submit">${creating ? 'Creează produsul' : 'Salvează produsul'}</button><button class="btn btn-ghost" type="button" data-editor-reload>${creating ? 'Resetează formularul' : 'Încarcă produsul actual'}</button></div>
    </form>
    ${editing ? html`<form class="card card-pad stack" data-catalog-stock><h3>Ajustează stocul</h3><p>Stoc la ultima citire: <strong data-stock-current>${num(product.stockCount)}</strong></p><p class="small muted">Ajustarea adaugă sau scade bucăți din stocul disponibil la salvare.</p>
      <div class="field"><label for="catalog-stock-delta">Diferență stoc</label><input class="input" id="catalog-stock-delta" name="delta" type="number" required min="-1000000" max="1000000" step="1"></div><div class="field"><label for="catalog-stock-reason">Motiv ajustare</label><input class="input" id="catalog-stock-reason" name="reason" required minlength="3" maxlength="200"></div><div data-stock-error></div><div><button class="btn btn-secondary" type="submit">Ajustează stocul</button></div></form>` : ''}
    </div><aside class="stack catalog-preview" aria-label="Previzualizarea produsului"><h3>Previzualizare</h3><div data-product-preview></div><p class="small muted">Previzualizarea se actualizează în timp ce scrii. Prețul final poate include oferte, reducerea de nivel sau un cod promo.</p></aside></div></div>`);
  const form = qs('[data-product-editor]', root);
  const save = qs('[type="submit"]', form);
  function draft() {
    const value = formData(form);
    return { ...value, fulfillment: badge ? 'BADGE' : value.fulfillment, price: Number(value.price), maxPerOrder: Number(value.maxPerOrder),
      active: form.elements.namedItem('active').checked, isNew: form.elements.namedItem('isNew').checked,
      ...(creating ? { stockCount: Number(value.stockCount) } : { version: editor.version }) };
  }
  function preview() {
    if (!active()) return;
    const value = draft();
    mount(qs('[data-product-preview]', root), html`<article class="card card-pad stack catalog-product-preview"><img src="${imgSrc(value.image)}" alt="" width="300" height="180"><div class="row"><span class="tag ${value.active ? 'tag-success' : ''}">${value.active ? 'Activ' : 'Inactiv'}</span>${value.isNew ? html`<span class="tag tag-brand">Nou</span>` : ''}</div><p class="small muted">${value.category || 'Categorie'}</p><h3>${value.name || 'Numele produsului'}</h3><p>${value.description || 'Descrierea scurtă a produsului.'}</p>${apAmount(value.price)}<p class="small muted">${FULFILLMENT[value.fulfillment]?.label || value.fulfillment} · maximum ${num(value.maxPerOrder)}/comandă · stoc ${num(creating ? value.stockCount : product.stockCount)}</p>${value.minTier ? html`<span class="tag">De la ${value.minTier}</span>` : ''}<details><summary>Descriere completă</summary><p class="catalog-full-description mt-1">${value.fullDescription || 'Detaliile produsului.'}</p></details></article>`);
    imageFallback();
  }
  async function discard() {
    if (!dirty) return true;
    return await openDialog({ title: 'Renunți la modificările nesalvate?', content: html`<p>Formularul va fi înlocuit. Modificările nesalvate se pierd.</p>`,
      footer: html`<button class="btn btn-ghost" type="button" data-close>Continuă editarea</button><button class="btn btn-primary" type="button" data-discard-editor>Renunță la modificări</button>`,
      onMount: (dlg, close) => qs('[data-discard-editor]', dlg).addEventListener('click', () => close(true)),
    }) === true && active();
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
  offs.push(on(root, 'input', '[data-product-editor] input, [data-product-editor] textarea, [data-product-editor] select', () => { dirty = true; preview(); }));
  offs.push(on(root, 'change', '[data-product-editor] select, [data-product-editor] input[type="checkbox"]', () => { dirty = true; preview(); }));
  offs.push(on(root, 'click', '[data-editor-reload]', async () => { if (active() && await discard()) { dirty = false; reload(); } }));
  offs.push(on(root, 'submit', '[data-product-editor]', (event) => {
    event.preventDefault();
    if (!active() || conflicted) return;
    busy(save, async () => {
      try {
        const body = draft();
        const result = creating ? await post(endpoint, body, { signal }) : await put(`${endpoint}/${enc(product.id)}`, body, { signal });
        if (!active()) return;
        dirty = false;
        toastOk(creating ? 'Produsul a fost creat.' : 'Produsul a fost salvat.');
        navigate(`${base}?id=${enc(result.id)}`, { replace: true });
      } catch (err) {
        if (!active() || isAbort(err)) return;
        conflicted = err.code === 'PRODUCT_CHANGED' || err.code === 'PRODUCT_VERSION_REQUIRED';
        mount(qs('[data-editor-error]', root), html`<p class="callout is-warning" role="alert">${err.message}${conflicted ? ' Modificările tale sunt încă în formular. Încarcă produsul actual pentru a vedea versiunea de pe server.' : ''}</p>`);
      }
    }).then(() => { if (active()) save.disabled = conflicted; });
  }));
  offs.push(on(root, 'submit', '[data-catalog-stock]', (event, stockForm) => {
    event.preventDefault();
    if (!active()) return;
    const button = qs('[type="submit"]', stockForm);
    busy(button, async () => {
      const value = formData(stockForm);
      const delta = Number(value.delta);
      if (!Number.isInteger(delta) || delta === 0) {
        mount(qs('[data-stock-error]', root), html`<p role="alert">Alege un număr întreg diferit de zero.</p>`); return;
      }
      const confirmed = await openDialog({ title: 'Confirmi ajustarea stocului?', content: html`<p>${delta > 0 ? 'Adaugi' : 'Scazi'} ${num(Math.abs(delta))} bucăți pentru ${product.name}. Ajustarea se aplică stocului disponibil la salvare și se consemnează în jurnal.</p>`,
        footer: html`<button class="btn btn-ghost" type="button" data-close>Renunță</button><button class="btn btn-primary" type="button" data-confirm-stock>Confirmă ajustarea</button>`,
        onMount: (dlg, close) => qs('[data-confirm-stock]', dlg).addEventListener('click', () => close(true)),
      });
      if (confirmed !== true || !active()) return;
      try {
        const result = await post(`${endpoint}/${enc(product.id)}/stock`, { delta, reason: value.reason }, { signal });
        if (!active()) return;
        product.stockCount = result.stockCount;
        qs('[data-stock-current]', root).textContent = num(result.stockCount);
        mount(qs('[data-stock-error]', root), '');
        stockForm.reset();
        preview();
        toastOk('Stocul a fost actualizat.');
      } catch (err) { if (active() && !isAbort(err)) mount(qs('[data-stock-error]', root), html`<p role="alert">${err.message}</p>`); }
    });
  }));
  preview();
  return () => { offs.forEach((off) => off()); window.removeEventListener('beforeunload', beforeUnload); document.removeEventListener('click', beforeLink, true); };
}
