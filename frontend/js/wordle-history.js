import { html, mount, join, on, qs } from './dom.js';
import { get, enc, query, isAbort } from './api.js';
import { state } from './store.js';
import { date, num, plural } from './format.js';
import { apAmount } from './components.js';
import { busy, copyText, handleError, modal, shareLink } from './ui.js';
import { SITE } from './seo.js';

const LABELS = { HIT: 'poziție corectă', NEAR: 'în cuvânt, altă poziție', MISS: 'nu apare', UNKNOWN: 'marcaj indisponibil' };

export async function wordleHistory(root, { signal, alive }) {
  const token = state.token;
  let disposed = false;
  let generation = 0;
  let page = 0;
  let filter = 'ALL';
  let data = { items: [], totalItems: 0, totalPages: 0 };
  const dialogs = new Set();
  const active = () => !disposed && alive() && state.token === token;

  function header() {
    return html`<div class="row-between"><div><h2>Istoricul meu Wordle</h2><p class="small muted">Rundele tale încheiate, cu grilele și scorurile păstrate.</p></div>
      <div class="field"><label for="wordle-history-filter">Rezultat</label><select class="select" id="wordle-history-filter" data-wordle-history-filter>
        <option value="ALL" ${filter === 'ALL' ? 'selected' : ''}>Toate rundele</option>
        <option value="WON" ${filter === 'WON' ? 'selected' : ''}>Câștigate</option>
        <option value="LOST" ${filter === 'LOST' ? 'selected' : ''}>Pierdute</option>
      </select></div></div>`;
  }

  function render() {
    mount(root, html`<section class="card card-pad stack wordle-history" aria-label="Istoricul meu Wordle">
      ${header()}
      ${data.items.length ? html`<p class="small muted">${plural(data.totalItems, 'rundă încheiată', 'runde încheiate')}</p>
        <div class="stack">${join(data.items, (item) => html`<article class="wordle-history-item stack stack-sm">
          <div class="row-between"><strong>Tech Wordle #${num(item.puzzleNumber)}</strong><time class="small muted" datetime="${item.date}">${date(item.date)}</time></div>
          <div class="row small"><span class="tag ${item.status === 'WON' ? 'tag-success' : ''}">${item.status === 'WON' ? 'Câștigată' : 'Pierdută'}</span><span>${item.attempts}/6 încercări</span>${apAmount(item.reward)}</div>
          <div class="row"><button class="btn btn-secondary btn-sm" type="button" data-wordle-replay="${item.date}" aria-label="Revezi runda din ${date(item.date)}">Revezi runda</button><button class="btn btn-ghost btn-sm" type="button" data-wordle-history-share="${item.date}" aria-label="Distribuie scorul din ${date(item.date)}">Distribuie scorul</button></div>
        </article>`)}</div>
        <nav class="row-between" aria-label="Paginarea rundelor Wordle"><button class="btn btn-secondary btn-sm" type="button" data-wordle-history-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>Mai recente</button><span class="small muted">Pagina ${num(page + 1)} din ${num(data.totalPages)}</span><button class="btn btn-secondary btn-sm" type="button" data-wordle-history-page="${page + 1}" ${page + 1 >= data.totalPages ? 'disabled' : ''}>Mai vechi</button></nav>`
      : html`<p class="muted">${filter === 'ALL' ? 'Nu ai încă runde încheiate. Joacă runda de azi și rezultatul va apărea aici.' : 'Nu ai runde care să corespundă acestui filtru.'}</p>`}
    </section>`);
  }

  async function refresh({ reset = false } = {}) {
    if (!active()) return;
    if (reset) page = 0;
    const request = ++generation;
    root.setAttribute('aria-busy', 'true');
    try {
      const result = await get('/games/wordle/history' + query({ page, size: 10, status: filter }), { signal });
      if (!active() || request !== generation) return;
      data = result;
      page = result.page;
      render();
    } catch (err) {
      if (!active() || request !== generation || isAbort(err)) return;
      mount(root, html`<section class="card card-pad stack">${header()}<p>Istoricul nu a putut fi încărcat.</p><div><button class="btn btn-secondary" type="button" data-wordle-history-retry>Reîncearcă</button></div></section>`);
    } finally { if (request === generation) root.removeAttribute('aria-busy'); }
  }

  function share(round) {
    if (!active() || !round.shareText) return;
    const url = SITE.origin + '/wordle';
    if (navigator.share) shareLink({ title: 'Tech Wordle', text: round.shareText, url });
    else copyText(`${round.shareText}\n${url}`, 'Scor copiat fără literele grilei.');
  }

  const offs = [
    on(root, 'change', '[data-wordle-history-filter]', (e, select) => { filter = select.value; refresh({ reset: true }); }),
    on(root, 'click', '[data-wordle-history-retry]', () => refresh()),
    on(root, 'click', '[data-wordle-history-page]', (e, b) => busy(b, async () => { page = Math.max(0, Number(b.dataset.wordleHistoryPage)); await refresh(); })),
    on(root, 'click', '[data-wordle-history-share]', (e, b) => {
      const round = data.items.find((item) => item.date === b.dataset.wordleHistoryShare);
      if (round) share(round);
    }),
    on(root, 'click', '[data-wordle-replay]', (e, b) => busy(b, async () => {
      try {
        const round = await get(`/games/wordle/history/${enc(b.dataset.wordleReplay)}`, { signal });
        if (!active()) return;
        await modal({
          title: `Tech Wordle #${num(round.puzzleNumber)} · ${date(round.date)}`,
          content: replayContent(round),
          footer: html`<button class="btn btn-primary" type="button" data-share-replay>Distribuie scorul</button>`,
          onMount: (dlg, close) => {
            dialogs.add(close);
            dlg.addEventListener('close', () => dialogs.delete(close), { once: true });
            qs('[data-share-replay]', dlg).addEventListener('click', () => share(round));
          },
        });
      } catch (err) { if (active() && !isAbort(err)) handleError(err); }
    })),
  ];
  await refresh();
  return {
    refresh,
    cleanup: () => {
      disposed = true;
      generation++;
      for (const close of [...dialogs]) close();
      offs.forEach((off) => off());
    },
  };
}

function replayContent(round) {
  return html`<div class="stack"><p class="strong">${round.status === 'WON' ? 'Rundă câștigată' : 'Rundă pierdută'} · ${round.guesses.length}/6 încercări</p>
    <p class="small muted">Rezultat arhivat · recompensa rundei ${apAmount(round.reward)}</p>
    <div class="wd-board wordle-replay-board" role="table" aria-label="Grila rundei arhivate">${join(round.guesses, (guess, index) => html`<div class="wd-row" role="row" aria-label="Încercarea ${index + 1}">${join([...guess.word], (letter, i) => html`<div class="wd-tile" role="cell" data-state="${guess.marks[i]}" aria-label="${letter}: ${LABELS[guess.marks[i]] || 'marcaj indisponibil'}">${letter}</div>`)}</div>`)}</div>
    ${round.answer ? html`<p>Cuvântul era <strong class="mono">${round.answer}</strong>.</p>${round.hint ? html`<p class="muted">${round.hint}</p>` : ''}` : html`<p class="small muted">Scorul este păstrat, dar grila acestei runde nu poate fi reconstituită.</p>`}
  </div>`;
}
