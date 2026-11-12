import { html, mount, join, qs, on, productImage, formData } from '../dom.js';
import { get, post, put, del, enc, isAbort } from '../api.js';
import { icon, coin } from '../icons.js';
import { num, date, rating as fmtRating, plural, percent } from '../format.js';
import {
  productBadges, wishButton, stars, apAmount, FULFILLMENT, tierName, productGrid, skeletonPage, avatar, countdownEl,
  emptyState, loginPrompt, progress, compareButton,
} from '../components.js';
import { setMeta, SITE } from '../seo.js';
import { isAuthed, state } from '../store.js';
import { addToCart } from '../actions.js';
import { navigate } from '../router.js';
import { busy, celebrate, confirmDialog, handleError, modal, shareLink, toastOk } from '../ui.js';

const SORTS = { helpful: 'Cele mai utile', recent: 'Cele mai noi', rating_desc: 'Note mari', rating_asc: 'Note mici' };
const REPORT_REASONS = { SPAM: 'Spam sau reclamă', OFFENSIVE: 'Limbaj ofensator', OFF_TOPIC: 'Nu e despre produs', FAKE: 'Pare falsă', OTHER: 'Alt motiv' };

export default async function product({ root, params, signal, alive }) {
  mount(root, skeletonPage());
  let data;
  try {
    data = await get(`/rewards/${enc(params.id)}`, { signal });
  } catch (err) {
    if (isAbort(err)) return;
    if (err.status === 404 || err.status === 400) {
      setMeta({ title: 'Premiu indisponibil', noindex: true });
      mount(root, emptyState({ icon: '🕵️', title: 'Premiul nu mai este disponibil', text: 'Poate a fost retras din catalog. Aruncă o privire la celelalte premii.', action: html`<a class="btn btn-primary" href="/magazin">Înapoi la magazin</a>` }));
      return;
    }
    throw err;
  }
  if (!alive()) return;
  const r = data.reward;
  const related = data.related || [];
  const f = FULFILLMENT[r.fulfillment] || FULFILLMENT.PHYSICAL;
  const maxQty = Math.max(1, Math.min(r.maxPerOrder || 1, r.stockCount || 1));

  setMeta({
    title: r.name,
    description: `${r.description}. ${r.fullDescription || ''} Revendică-l cu ${num(r.deal ? r.deal.dealPrice : r.price)} AP în RewardHub.`.slice(0, 300),
    image: productImage(r),
    jsonld: {
      '@context': 'https://schema.org',
      '@type': 'Product',
      name: r.name,
      description: r.fullDescription || r.description,
      sku: r.id,
      category: r.category,
      image: SITE.origin + productImage(r),
      brand: { '@type': 'Brand', name: 'Generația Tech' },
      ...(r.reviewCount ? { aggregateRating: { '@type': 'AggregateRating', ratingValue: r.rating, reviewCount: r.reviewCount, bestRating: 5, worstRating: 1 } } : {}),
    },
  });

  mount(root, html`<div class="page">
    <nav class="breadcrumbs" aria-label="Breadcrumb">
      <a href="/magazin">Magazin</a>${icon('chevronRight')}
      <a href="/magazin?cat=${enc(r.category)}">${r.category}</a>${icon('chevronRight')}
      <span aria-current="page" class="ellipsis">${r.name}</span>
    </nav>

    <div class="product">
      <div class="product-media">
        <img src="${productImage(r)}" alt="${r.name}" width="480" height="480">
        ${productBadges(r)}
        ${wishButton(r.id, r.name)}
      </div>

      <div class="product-info">
        <div class="row">
          <span class="tag">${f.icon} ${f.label}</span>
          ${r.soldLast30Days ? html`<span class="tag tag-success">🔥 ${plural(r.soldLast30Days, 'revendicare', 'revendicări')} luna asta</span>` : ''}
        </div>
        <h1>${r.name}</h1>
        <a class="rating" href="#recenzii">${r.reviewCount ? html`${stars(r.rating)} <strong>${fmtRating(r.rating)}</strong> · ${plural(r.reviewCount, 'recenzie', 'recenzii')}` : html`${stars(null, false)} Fii primul care lasă o recenzie`}</a>
        <p class="prose">${r.description}</p>

        <div class="product-price">
          <span class="price-now ${r.deal ? 'is-deal' : ''}">${coin()}${num(r.deal ? r.deal.dealPrice : r.price)}<span class="small muted">AP</span></span>
          ${r.deal ? html`<span class="price-old">${num(r.price)} AP</span><span class="tag tag-hot">−${r.deal.discountPercent}%</span>` : ''}
        </div>

        ${r.deal ? html`<div class="deal-box">
          <div class="row-between"><strong>⚡ Ofertă flash</strong>${countdownEl(r.deal.endsAt, { prefix: 'Se termină în' })}</div>
          <div class="small muted">${r.deal.remaining} bucăți la preț redus · maximum ${r.deal.perUserLimit} de persoană. Bucățile peste limită se taxează la prețul întreg.</div>
        </div>` : ''}

        <div class="buy-box">
          ${r.minTier ? html`<div class="callout is-warning">${icon('lock')}<span>Disponibil începând cu nivelul <strong>${tierName(r.minTier)}</strong>. Urci de nivel acumulând XP din activități.</span></div>` : ''}
          ${r.inStock ? html`
            <div class="row-between small">
              <span class="${r.lowStock ? 'pcard-stock' : 'muted'}">${r.lowStock ? `Doar ${plural(r.stockCount, 'bucată rămasă', 'bucăți rămase')}!` : r.fulfillment === 'PHYSICAL' ? 'În stoc · livrare prin curier' : 'Disponibil · livrare instant'}</span>
              ${isAuthed() && state.user ? html`<span class="muted">Soldul tău: ${apAmount(state.user.activityPoints)}</span>` : ''}
            </div>
            <div class="buy-row">
              ${maxQty > 1 ? html`<div class="qty" role="group" aria-label="Cantitate">
                <button type="button" data-qty-step="-1" aria-label="Scade cantitatea" disabled>${icon('minus')}</button>
                <output id="qty" aria-live="polite">1</output>
                <button type="button" data-qty-step="1" aria-label="Crește cantitatea">${icon('plus')}</button>
              </div>` : ''}
              <button class="btn btn-primary btn-lg" type="button" data-buy="cart">${icon('cart')} Adaugă în coș</button>
              <button class="btn btn-gold btn-lg" type="button" data-buy="now">Revendică acum</button>
            </div>`
          : html`<div class="callout is-danger">${icon('alert')}<span><strong>Stoc epuizat.</strong> Salvează premiul la favorite și te anunțăm când revine în stoc.</span></div>
            <button class="btn btn-secondary" type="button" data-wish="${r.id}" aria-pressed="${String(state.wishlist.has(r.id))}">${icon('heart')} Anunță-mă</button>`}
          <div class="row small muted">
            ${compareButton(r)}
            <button class="btn btn-secondary btn-sm" type="button" data-goal-set="${r.id}">🎯 Setează ca obiectiv</button>
            <button class="btn btn-ghost btn-sm" type="button" data-share>${icon('share')} Distribuie</button>
            ${r.wishlistCount ? html`<span>❤️ ${plural(r.wishlistCount, 'membru îl are', 'membri îl au')} la favorite</span>` : ''}
          </div>
        </div>

        <section class="stack" aria-labelledby="det-h">
          <h2 id="det-h" class="card-title">Detalii</h2>
          <p class="prose">${r.fullDescription || r.description}</p>
          <ul class="perks-list">
            <li>${icon('check')} ${f.hint}</li>
            <li>${icon('check')} Plata se face exclusiv în AP, puncte câștigate din activitate.</li>
            <li>${icon('check')} ${r.fulfillment === 'PHYSICAL' ? 'Poți anula comanda cât timp nu a fost expediată; AP-ul revine integral.' : 'Codul de voucher apare imediat în pagina comenzii.'}</li>
            ${r.maxPerOrder ? html`<li>${icon('check')} Maximum ${r.maxPerOrder} ${r.maxPerOrder === 1 ? 'bucată' : 'bucăți'} pe comandă.</li>` : ''}
          </ul>
        </section>
      </div>
    </div>

    <section class="card card-pad stack" id="recenzii" aria-labelledby="rev-h">
      <div class="row-between">
        <h2 id="rev-h">Recenzii</h2>
        <label class="row small"><span class="muted">Ordonează</span>
          <select class="select" id="rev-sort" aria-label="Ordonează recenziile">
            ${join(Object.entries(SORTS), ([k, v]) => html`<option value="${k}">${v}</option>`)}
          </select>
        </label>
      </div>
      <div id="rev-box"><div class="skel skel-block"></div></div>
    </section>

    ${related.length ? html`<section class="section" aria-labelledby="rel-h"><h2 id="rel-h">S-ar putea să-ți placă și</h2>${productGrid(related)}</section>` : ''}
  </div>`);

  // ---------------------------------------------------------------- cumparare
  let qty = 1;
  const offs = [];
  offs.push(on(root, 'click', '[data-qty-step]', (e, b) => {
    qty = Math.max(1, Math.min(maxQty, qty + Number(b.dataset.qtyStep)));
    qs('#qty', root).textContent = String(qty);
    qs('[data-qty-step="-1"]', root).disabled = qty <= 1;
    qs('[data-qty-step="1"]', root).disabled = qty >= maxQty;
  }));
  offs.push(on(root, 'click', '[data-buy]', async (e, b) => {
    const cart = await addToCart(b, r.id, qty);
    if (cart && b.dataset.buy === 'now') navigate('/cos');
  }));
  offs.push(on(root, 'click', '[data-share]', () => shareLink({
    title: r.name, text: `${r.name} — îl poți revendica cu AP în RewardHub`, url: `${SITE.origin}/produs/${enc(r.id)}`,
  })));

  // ---------------------------------------------------------------- recenzii
  const rev = { sort: 'helpful', page: 0, items: [], data: null };
  const box = qs('#rev-box', root);

  async function loadReviews(append = false) {
    try {
      const d = await get(`/rewards/${enc(r.id)}/reviews?sort=${rev.sort}&page=${rev.page}&size=10`, { signal });
      if (!alive()) return;
      rev.data = d;
      rev.items = append ? rev.items.concat(d.reviews || []) : d.reviews || [];
      renderReviews();
    } catch (err) {
      if (!isAbort(err)) mount(box, html`<p class="muted">Nu am putut încărca recenziile. ${err.message}</p>`);
    }
  }

  function renderReviews() {
    const d = rev.data;
    const total = d.totalReviews || 0;
    mount(box, html`<div class="stack stack-lg">
      ${total ? html`<div class="reviews-summary">
        <div class="stack stack-sm">
          <div class="score-big">${fmtRating(d.averageRating)}</div>
          ${stars(d.averageRating)}
          <span class="small muted">${plural(total, 'recenzie', 'recenzii')} de la membri</span>
        </div>
        <div class="dist">${join([5, 4, 3, 2, 1], (s) => html`<div class="dist-row"><span>${s} ★</span>${progress(percent(d.distribution?.[s] || 0, total), '', `${s} stele`)}<span>${d.distribution?.[s] || 0}</span></div>`)}</div>
      </div>` : ''}
      ${myReviewBlock(d)}
      ${rev.items.length
        ? html`<div>${join(rev.items, reviewItem)}</div>`
        : !d.myReview ? html`<p class="muted">Încă nu există recenzii. Recenziile vin doar de la membri care au primit produsul, așa că sunt 100% reale.</p>` : ''}
      ${d.hasMore ? html`<div class="load-more"><button class="btn btn-secondary" type="button" data-rev-more>Mai multe recenzii</button></div>` : ''}
    </div>`);
  }

  function myReviewBlock(d) {
    if (d.myReview) {
      return html`<div class="review is-mine">
        <div class="row-between"><strong>Recenzia ta</strong>
          <div class="row"><button class="btn btn-ghost btn-sm" type="button" data-rev-edit>${icon('edit')} Editează</button>
          <button class="btn btn-ghost btn-sm" type="button" data-rev-del>${icon('trash')} Șterge</button></div></div>
        <div class="row">${stars(d.myReview.rating)}${d.myReview.hidden ? html`<span class="tag tag-danger">Ascunsă de moderator</span>` : ''}</div>
        <p class="review-body">${d.myReview.comment}</p>
      </div>`;
    }
    const el = d.eligibility || {};
    if (el.canReview) return reviewForm(null, el.rewardPoints);
    if (el.reason === 'LOGIN_REQUIRED') return loginPrompt('Intră în cont ca să vezi dacă poți lăsa o recenzie.', `/produs/${r.id}#recenzii`);
    if (el.reason === 'PURCHASE_REQUIRED') return html`<div class="callout">${icon('info')}<span>Poți lăsa o recenzie după ce primești acest premiu. Așa fiecare recenzie vine de la cineva care chiar l-a folosit.</span></div>`;
    return '';
  }

  function reviewForm(existing, rewardPoints) {
    return html`<form class="card card-pad stack" id="rev-form" novalidate>
      <strong>${existing ? 'Editează recenzia' : 'Cum ți s-a părut?'}${!existing && rewardPoints ? html` <span class="tag tag-gold">+${rewardPoints} AP</span>` : ''}</strong>
      <fieldset class="star-input" aria-label="Nota ta">
        ${join([5, 4, 3, 2, 1], (s) => html`<input type="radio" id="star-${s}" name="rating" value="${s}" ${existing?.rating === s ? 'checked' : ''}><label for="star-${s}" title="${s} stele" aria-label="${s} ${s === 1 ? 'stea' : 'stele'}">★</label>`)}
      </fieldset>
      <div class="field">
        <label for="rev-comment">Recenzia ta</label>
        <textarea class="textarea" id="rev-comment" name="comment" minlength="10" maxlength="1000" required placeholder="Ce ți-a plăcut? Cum îl folosești? (minimum 10 caractere)">${existing?.comment || ''}</textarea>
        <span class="hint"><span id="rev-len">${(existing?.comment || '').length}</span>/1000</span>
        <span class="error" id="rev-err" hidden></span>
      </div>
      <div class="row">
        <button class="btn btn-primary" type="submit">${existing ? 'Salvează' : 'Publică recenzia'}</button>
        ${existing ? html`<button class="btn btn-ghost" type="button" data-rev-cancel>Renunță</button>` : ''}
      </div>
    </form>`;
  }

  function reviewItem(x) {
    const name = x.author?.name || 'Membru';
    return html`<article class="review">
      <div class="review-head">
        ${avatar(x.author?.avatar, name, 'sm')}
        ${x.author?.id ? html`<a class="review-author" href="/membru/${enc(x.author.id)}">${name}</a>` : html`<span class="review-author">${name}</span>`}
        ${x.author?.tierIcon ? html`<span title="${tierName(x.author.tier)}">${x.author.tierIcon}</span>` : ''}
        ${x.verified ? html`<span class="tag tag-success">${icon('check')} Achiziție verificată</span>` : ''}
      </div>
      <div class="row small muted">${stars(x.rating)}<span>${date(x.createdAt)}${x.updatedAt ? ' · editată' : ''}</span></div>
      <p class="review-body">${x.comment}</p>
      ${!x.mine ? html`<div class="review-actions">
        <button class="btn btn-ghost btn-sm" type="button" data-helpful="${x.id}" aria-pressed="${String(Boolean(x.votedHelpful))}">${icon('thumbsUp')} Utilă${x.helpfulCount ? html` · <span class="tnum">${x.helpfulCount}</span>` : ''}</button>
        <button class="btn btn-ghost btn-sm" type="button" data-report="${x.id}">${icon('flag')} Raportează</button>
      </div>` : x.helpfulCount ? html`<span class="small muted">${plural(x.helpfulCount, 'membru a găsit-o utilă', 'membri au găsit-o utilă')}</span>` : ''}
    </article>`;
  }

  async function submitReview(form, existing) {
    const fd = formData(form);
    const rating = Number(fd.rating);
    const comment = fd.comment || '';
    const err = qs('#rev-err', form);
    const fail = (m) => { err.textContent = m; err.hidden = false; };
    if (!rating) return fail('Alege o notă de la 1 la 5 stele.');
    if (comment.length < 10) return fail('Recenzia trebuie să aibă cel puțin 10 caractere.');
    const btn = qs('[type=submit]', form);
    await busy(btn, async () => {
      try {
        const res = existing
          ? await put(`/rewards/${enc(r.id)}/reviews/mine`, { rating, comment })
          : await post(`/rewards/${enc(r.id)}/reviews`, { rating, comment });
        toastOk(res.message || 'Mulțumim pentru recenzie!', { icon: '⭐' });
        await celebrate(res);
        rev.page = 0;
        await loadReviews();
      } catch (e) { fail(e.message); }
    });
  }

  offs.push(on(root, 'change', '#rev-sort', (e, s) => { rev.sort = s.value; rev.page = 0; loadReviews(); }));
  offs.push(on(root, 'click', '[data-rev-more]', (e, b) => busy(b, () => { rev.page += 1; return loadReviews(true); })));
  offs.push(on(root, 'input', '#rev-comment', (e, t) => { const l = qs('#rev-len', root); if (l) l.textContent = String(t.value.length); }));
  offs.push(on(root, 'submit', '#rev-form', (e, form) => { e.preventDefault(); submitReview(form, !!form.dataset.editing); }));
  offs.push(on(root, 'click', '[data-rev-edit]', () => {
    const mine = rev.data.myReview;
    const block = qs('.review.is-mine', root);
    const tpl = document.createElement('div');
    mount(tpl, reviewForm(mine, 0));
    const form = tpl.firstElementChild;
    form.dataset.editing = '1';
    block.replaceWith(form);
    qs('#rev-comment', form).focus();
  }));
  offs.push(on(root, 'click', '[data-rev-cancel]', () => renderReviews()));
  offs.push(on(root, 'click', '[data-rev-del]', async (e, b) => {
    const ok = await confirmDialog({ title: 'Ștergi recenzia?', body: 'Recenzia dispare definitiv. AP-ul primit pentru ea nu se mai acordă a doua oară pentru acest produs.', confirmText: 'Șterge', danger: true });
    if (!ok) return;
    await busy(b, async () => {
      try {
        const res = await del(`/rewards/${enc(r.id)}/reviews/mine`);
        toastOk(res.message);
        rev.page = 0;
        await loadReviews();
      } catch (err) { handleError(err); }
    });
  }));
  offs.push(on(root, 'click', '[data-helpful]', async (e, b) => {
    if (!isAuthed()) {
      navigate(`/autentificare?next=${enc(`/produs/${r.id}`)}`);
      return;
    }
    await busy(b, async () => {
      try {
        const res = await post(`/reviews/${enc(b.dataset.helpful)}/helpful`);
        const item = rev.items.find((x) => x.id === b.dataset.helpful);
        if (item) { item.votedHelpful = res.helpful; item.helpfulCount = res.helpfulCount; }
        renderReviews();
      } catch (err) { handleError(err); }
    });
  }));
  offs.push(on(root, 'click', '[data-report]', async (e, b) => {
    if (!isAuthed()) {
      navigate(`/autentificare?next=${enc(`/produs/${r.id}`)}`);
      return;
    }
    const reason = await modal({
      title: 'Raportează recenzia',
      content: html`<p class="muted">Ne ajuți să păstrăm recenziile utile și corecte. Alege motivul:</p>
        <form id="rep-form" class="stack stack-sm">${join(Object.entries(REPORT_REASONS), ([k, v], i) => html`<label class="check"><input type="radio" name="reason" value="${k}" ${i === 0 ? 'checked' : ''}> ${v}</label>`)}</form>`,
      footer: html`<button class="btn btn-ghost" type="button" data-close>Renunță</button><button class="btn btn-danger" type="submit" form="rep-form">Trimite raportul</button>`,
      onMount: (dlg, close) => qs('#rep-form', dlg).addEventListener('submit', (ev) => { ev.preventDefault(); close(formData(ev.target).reason); }),
    });
    if (!reason) return;
    try {
      const res = await post(`/reviews/${enc(b.dataset.report)}/report`, { reason });
      toastOk(res.message, { icon: '🛡️' });
      b.disabled = true;
    } catch (err) { handleError(err); }
  }));

  await loadReviews();
  return () => offs.forEach((off) => off());
}
