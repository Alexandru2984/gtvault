import { html, mount, join, qs, on, formData } from '../dom.js';
import { get, post, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { num, rating as fmtRating, timeAgo } from '../format.js';
import { PROPOSAL_STATUS, emptyState, loginPrompt, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { isAuthed } from '../store.js';
import { busy, celebrate, handleError, modal, toastOk } from '../ui.js';
import { feedItem } from './home.js';

export default async function community({ root, query, signal, alive }) {
  setMeta({ title: 'Comunitate', description: 'Ce se întâmplă în comunitatea Generația Tech: insigne deblocate, recenzii, premii revendicate și propuneri de premii noi votate de membri.' });
  mount(root, skeletonPage());
  let sort = query.sort === 'new' ? 'new' : 'top';
  let stats;
  let feed;
  let proposals;
  let categories;
  try {
    [stats, feed, proposals, categories] = await Promise.all([
      get('/community/stats', { signal }).catch(() => null),
      get('/community/activity?limit=25', { signal }).catch(() => []),
      get(`/proposals?sort=${sort}`, { signal }),
      get('/proposals/categories', { signal }).catch(() => []),
    ]);
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  function render() {
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Comunitate</h1><p>Propune premiile pe care le vrei în magazin, votează ideile altora și vezi ce se mișcă în comunitate.</p></div>
        <div class="row"><a class="btn btn-ghost btn-sm" href="/clasament">${icon('trophy')} Clasament</a><a class="btn btn-ghost btn-sm" href="/bresle">${icon('shield')} Bresle</a></div>
      </div>
      ${stats ? html`<div class="kpis">
        <div class="kpi"><span class="kpi-label">Membri</span><span class="kpi-value">${num(stats.members)}</span><span class="kpi-sub">${num(stats.activeToday)} activi azi</span></div>
        <div class="kpi"><span class="kpi-label">XP săptămâna asta</span><span class="kpi-value">${num(stats.xpEarnedThisWeek)}</span><span class="kpi-sub">${num(stats.ordersThisWeek)} premii revendicate</span></div>
        <div class="kpi"><span class="kpi-label">Recenzii</span><span class="kpi-value">${num(stats.reviews)}</span><span class="kpi-sub">${stats.averageRating ? `notă medie ${fmtRating(stats.averageRating)} ★` : 'scrie prima recenzie'}</span></div>
        <div class="kpi"><span class="kpi-label">Tech Wordle azi</span><span class="kpi-value">${num(stats.wordleSolversToday)}</span><span class="kpi-sub">l-au ghicit</span></div>
      </div>` : ''}
      <div class="cart-layout">
        <section class="stack" aria-labelledby="prop-h">
          <div class="row-between">
            <h2 id="prop-h">💡 Propuneri de premii</h2>
            <div class="row">
              <div class="tabs" role="tablist" aria-label="Ordonare">
                <button type="button" role="tab" data-sort="top" aria-selected="${sort === 'top'}">Top</button>
                <button type="button" role="tab" data-sort="new" aria-selected="${sort === 'new'}">Noi</button>
              </div>
              ${isAuthed() ? html`<button class="btn btn-primary btn-sm" type="button" data-propose>${icon('plus')} Propune</button>` : ''}
            </div>
          </div>
          ${!isAuthed() ? loginPrompt('Intră în cont ca să votezi și să propui premii (+15 AP pe propunere, +5 AP pe vot).') : ''}
          ${proposals.length ? html`<div class="stack">${join(proposals, proposal)}</div>`
            : emptyState({ icon: '💡', title: 'Nicio propunere încă', text: 'Ce premiu ți-ar plăcea în magazin? Fii primul care propune!' })}
        </section>
        <aside class="card card-pad summary-sticky" aria-labelledby="feed-h">
          <h2 class="card-title" id="feed-h">${icon('sparkles')} Activitate recentă</h2>
          ${feed?.length ? html`<ul class="feed mt-1">${join(feed, feedItem)}</ul>` : html`<p class="muted">Liniște... deocamdată. 🌙</p>`}
        </aside>
      </div>
    </div>`);
  }

  function proposal(p) {
    const st = PROPOSAL_STATUS[p.status] || { label: p.status, cls: '' };
    return html`<article class="card proposal">
      <button class="vote-btn" type="button" data-vote="${p.id}" aria-pressed="${p.hasVoted}" aria-label="${p.hasVoted ? 'Retrage votul' : 'Votează'} „${p.title}”, ${p.votesCount} voturi" ${!isAuthed() || p.isMine || p.status !== 'PENDING' ? 'disabled' : ''}>
        ${icon('chevronUp')}<span class="tnum">${num(p.votesCount)}</span>
      </button>
      <div class="stack stack-sm">
        <div class="row"><span class="tag ${st.cls}">${st.label}</span><span class="tag">${p.category}</span>${p.isMine ? html`<span class="tag tag-brand">a ta</span>` : ''}</div>
        <h3 class="proposal-title">${p.title}</h3>
        <p class="proposal-desc">${p.description}</p>
        ${p.adminNote ? html`<p class="admin-note"><strong>Echipa:</strong> ${p.adminNote}</p>` : ''}
        <span class="xsmall muted">de ${p.authorName} · ${timeAgo(p.createdAt)}</span>
      </div>
    </article>`;
  }

  async function reload() {
    proposals = await get(`/proposals?sort=${sort}`, { signal });
    if (alive()) render();
  }

  render();
  const offs = [
    on(root, 'click', '[data-sort]', async (e, b) => {
      sort = b.dataset.sort;
      setQuery({ sort: sort === 'top' ? '' : sort });
      try { await reload(); } catch (err) { handleError(err); }
    }),
    on(root, 'click', '[data-vote]', (e, b) => busy(b, async () => {
      try {
        const r = await post(`/proposals/${enc(b.dataset.vote)}/vote`);
        const p = proposals.find((x) => x.id === b.dataset.vote);
        if (p) { p.hasVoted = r.hasVoted; p.votesCount = r.votesCount; }
        toastOk(r.message, { icon: r.hasVoted ? '👍' : '↩️', timeout: 2400 });
        await celebrate(r);
        render();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-propose]', () => openProposalForm(categories, async (created) => {
      proposals = [created, ...proposals.filter((x) => x.id !== created.id)];
      render();
    })),
  ];
  return () => offs.forEach((off) => off());
}

function openProposalForm(categories, onCreated) {
  return modal({
    title: 'Propune un premiu',
    content: html`<form class="stack" id="prop-form" novalidate>
      <div class="field"><label for="p-title">Ce premiu ai vrea?</label><input class="input" id="p-title" name="title" minlength="5" maxlength="80" required placeholder="Ex: Licență JetBrains pe un an"></div>
      <div class="field"><label for="p-cat">Categorie</label><select class="select" id="p-cat" name="category" required>${join(categories, (c) => html`<option>${c}</option>`)}</select></div>
      <div class="field"><label for="p-desc">De ce ar fi util?</label><textarea class="textarea" id="p-desc" name="description" minlength="10" maxlength="255" required placeholder="Câteva cuvinte despre premiu și cui i-ar folosi."></textarea><span class="hint">10–255 caractere</span></div>
      <div class="form-error" role="alert" hidden></div>
    </form>`,
    footer: html`<button class="btn btn-ghost" type="button" data-close>Renunță</button><button class="btn btn-primary" type="submit" form="prop-form">Publică · +15 AP</button>`,
    onMount: (dlg, close) => {
      const form = qs('#prop-form', dlg);
      const err = qs('.form-error', dlg);
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const d = formData(form);
        const fail = (m) => { err.textContent = m; err.hidden = false; };
        if ((d.title || '').length < 5) return fail('Titlul trebuie să aibă cel puțin 5 caractere.');
        if ((d.description || '').length < 10) return fail('Descrierea trebuie să aibă cel puțin 10 caractere.');
        await busy(qs('[type=submit]', dlg), async () => {
          try {
            const r = await post('/proposals', d);
            toastOk(r.message, { icon: '💡' });
            await celebrate(r);
            onCreated(r.proposal);
            close(true);
          } catch (e2) { fail(e2.message); }
        });
        return undefined;
      });
    },
  });
}

