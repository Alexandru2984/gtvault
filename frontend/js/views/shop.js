import { html, mount, join, qs, on } from '../dom.js';
import { get, query, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { plural } from '../format.js';
import { productGrid, skeletonGrid, emptyState } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { handleError } from '../ui.js';
import { comparedIds, compareLink } from '../compare.js';

const SORT_LABELS = {
  featured: 'Recomandate',
  popular: 'Cele mai populare',
  rating: 'Cele mai apreciate',
  new: 'Noutăți',
  price_asc: 'Preț crescător',
  price_desc: 'Preț descrescător',
};

export default async function shop({ root, query: q0, signal, alive }) {
  const f = {
    q: (q0.q || '').slice(0, 80),
    cat: q0.cat || '',
    sort: SORT_LABELS[q0.sort] ? q0.sort : 'featured',
    stoc: q0.stoc === '1',
    oferte: q0.oferte === '1',
  };
  setMeta({
    title: f.cat ? `${f.cat} — magazin` : 'Magazin de premii',
    description: 'Toate premiile RewardHub: tricouri și stickere Generația Tech, vouchere digitale, cursuri, mentorat 1:1 și gadgeturi pe care le revendici cu AP.',
  });

  mount(root, html`<div class="page">
    <div class="page-head">
      <div>
        <h1>Magazin</h1>
        <p>Transformă punctele de activitate în premii. Prețurile includ automat ofertele flash active.</p>
      </div>
      <a class="btn btn-secondary" href="${compareLink()}" data-compare-link>Compară (${comparedIds().length})</a>
    </div>
    <div class="shop-tools">
      <form class="shop-search" role="search" data-shop-search>
        ${icon('search')}
        <label class="sr-only" for="shop-q">Caută în magazin</label>
        <input class="input" id="shop-q" name="q" type="search" placeholder="Caută: tricou, Udemy, mentorat…" value="${f.q}" autocomplete="off" enterkeyhint="search" maxlength="80">
      </form>
      <div class="chips" id="shop-cats" role="group" aria-label="Categorii"></div>
      <div class="shop-filters">
        <div class="row">
          <button class="chip" type="button" data-toggle="oferte" aria-pressed="${f.oferte}">${icon('zap')} Oferte</button>
          <button class="chip" type="button" data-toggle="stoc" aria-pressed="${f.stoc}">${icon('check')} În stoc</button>
        </div>
        <label class="row small">
          <span class="muted">Sortează</span>
          <select class="select" id="shop-sort" aria-label="Sortează premiile">
            ${join(Object.entries(SORT_LABELS), ([k, label]) => html`<option value="${k}" ${k === f.sort ? 'selected' : ''}>${label}</option>`)}
          </select>
        </label>
      </div>
    </div>
    <p class="small muted" id="shop-count" aria-live="polite"></p>
    <div id="shop-results">${skeletonGrid(8)}</div>
  </div>`);

  let ctrl = null;
  let categories = [];

  function renderCats() {
    mount(qs('#shop-cats', root), html`
      <button class="chip" type="button" data-cat="" aria-pressed="${!f.cat}">Toate</button>
      ${join(categories, (c) => html`<button class="chip" type="button" data-cat="${c}" aria-pressed="${f.cat === c}">${c}</button>`)}`);
  }

  async function load() {
    ctrl?.abort();
    ctrl = new AbortController();
    const local = ctrl;
    const onAbort = () => local.abort();
    signal.addEventListener('abort', onAbort, { once: true });
    setQuery({ q: f.q, cat: f.cat, sort: f.sort === 'featured' ? '' : f.sort, stoc: f.stoc ? 1 : '', oferte: f.oferte ? 1 : '' });
    try {
      const r = await get(`/rewards${query({ q: f.q, category: f.cat, sort: f.sort, inStock: f.stoc })}`, { signal: local.signal });
      if (!alive() || local.signal.aborted) return;
      categories = r.categories || categories;
      renderCats();
      let list = r.rewards || [];
      if (f.oferte) list = list.filter((x) => x.deal);
      qs('#shop-count', root).textContent = f.q || f.cat || f.stoc || f.oferte
        ? `${plural(list.length, 'premiu găsit', 'premii găsite')}`
        : `${plural(list.length, 'premiu', 'premii')} în catalog`;
      mount(qs('#shop-results', root), list.length ? productGrid(list) : emptyState({
        icon: '🔍',
        title: 'Niciun premiu nu se potrivește',
        text: f.oferte ? 'Nu sunt oferte flash active pe filtrul ales. Revino mâine — ofertele se schimbă zilnic.' : 'Încearcă alt termen de căutare sau șterge filtrele.',
        action: html`<button class="btn btn-secondary" type="button" data-reset>Șterge filtrele</button>`,
      }));
    } catch (err) {
      if (!isAbort(err)) {
        handleError(err);
        mount(qs('#shop-results', root), emptyState({ icon: '🔌', title: 'Nu am putut încărca premiile', text: err.message, action: html`<button class="btn btn-primary" type="button" data-retry>Reîncearcă</button>` }));
      }
    } finally {
      signal.removeEventListener('abort', onAbort);
    }
  }

  let timer = null;
  const input = qs('#shop-q', root);
  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      f.q = input.value.trim().slice(0, 80);
      load();
    }, 280);
  });
  const offs = [
    on(root, 'submit', '[data-shop-search]', (e) => {
      e.preventDefault();
      clearTimeout(timer);
      f.q = input.value.trim().slice(0, 80);
      input.blur();
      load();
    }),
    on(root, 'click', '[data-cat]', (e, b) => { f.cat = b.dataset.cat; load(); }),
    on(root, 'click', '[data-toggle]', (e, b) => {
      const k = b.dataset.toggle;
      f[k] = !f[k];
      b.setAttribute('aria-pressed', String(f[k]));
      load();
    }),
    on(root, 'change', '#shop-sort', (e, s) => { f.sort = s.value; load(); }),
    on(root, 'click', '[data-reset]', () => {
      Object.assign(f, { q: '', cat: '', sort: 'featured', stoc: false, oferte: false });
      input.value = '';
      qs('#shop-sort', root).value = 'featured';
      for (const b of root.querySelectorAll('[data-toggle]')) b.setAttribute('aria-pressed', 'false');
      load();
    }),
    on(root, 'click', '[data-retry]', () => load()),
  ];
  await load();
  return () => {
    clearTimeout(timer);
    ctrl?.abort();
    offs.forEach((off) => off());
  };
}
