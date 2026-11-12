import { html, mount, join, on, qs } from './dom.js';
import { get, query, enc, isAbort } from './api.js';
import { state } from './store.js';
import { date, num, percent, plural } from './format.js';
import { apAmount } from './components.js';
import { busy, handleError, modal } from './ui.js';

export async function quizNotebook(root, { signal, alive }) {
  const token = state.token;
  let disposed = false;
  let generation = 0;
  let page = 0;
  let result = 'ALL';
  let category = '';
  let data = { items: [], summary: { categories: [], answered: 0, correct: 0, rewardPoints: 0 } };
  const dialogs = new Set();
  const active = () => !disposed && alive() && state.token === token;

  function header() {
    return html`<div><h2>Caietul meu de quiz</h2><p class="small muted">Revezi explicațiile și exersează întrebările rezolvate. Recapitularea nu acordă AP.</p></div>
      <div class="quiz-notebook-filters">
        <div class="field"><label for="quiz-result-filter">Rezultatul la quiz</label><select class="select" id="quiz-result-filter" data-quiz-result-filter>
          ${join([['ALL', 'Toate răspunsurile'], ['WRONG', 'De recapitulat'], ['CORRECT', 'Corecte']], ([value, label]) => html`<option value="${value}" ${result === value ? 'selected' : ''}>${label}</option>`)}
        </select></div>
        <div class="field"><label for="quiz-category-filter">Categoria quiz-ului</label><select class="select" id="quiz-category-filter" data-quiz-category-filter>
          <option value="">Toate categoriile</option>${join(data.summary.categories, (c) => html`<option value="${c.name}" ${category === c.name ? 'selected' : ''}>${c.name} · ${c.correct}/${c.answered}</option>`)}
        </select></div>
      </div>`;
  }

  function render() {
    const s = data.summary;
    mount(root, html`<section class="card card-pad stack quiz-notebook" aria-label="Caietul meu de quiz">
      ${header()}
      <div class="row quiz-notebook-summary" aria-label="Progresul meu la quiz"><span class="tag">${num(s.answered)} ${s.answered === 1 ? 'rezolvat' : 'rezolvate'}</span><span class="tag tag-success">${num(s.correct)} ${s.correct === 1 ? 'corect' : 'corecte'} · ${percent(s.correct, s.answered)}%</span><span class="tag">${num(s.rewardPoints)} AP din quiz</span></div>
      <p class="small muted">Caietul păstrează răspunsurile trimise după activarea funcției. Statisticile sunt pentru întregul caiet.</p>
      ${data.items.length ? html`<p class="small muted" role="status">${plural(data.totalItems, 'răspuns găsit', 'răspunsuri găsite')}</p>
        <div class="stack">${join(data.items, (item) => html`<article class="quiz-notebook-item stack stack-sm">
          <div class="row-between"><span class="tag tag-brand">${item.category}</span><time class="small muted" datetime="${item.date}">${date(item.date)}</time></div>
          <h3 class="quiz-notebook-question">${item.question}</h3>
          <div class="row-between"><span class="tag ${item.correct ? 'tag-success' : ''}">${item.correct ? 'Corect' : 'De recapitulat'}</span><button class="btn btn-secondary btn-sm" type="button" data-quiz-review="${item.date}" aria-label="Revezi quiz-ul din ${date(item.date)}">Revezi și exersează</button></div>
        </article>`)}</div>
        <nav class="row-between" aria-label="Paginarea caietului de quiz"><button class="btn btn-secondary btn-sm" type="button" data-quiz-notebook-page="${page - 1}" ${page === 0 ? 'disabled' : ''}>Mai recente</button><span class="small muted">Pagina ${num(page + 1)} din ${num(data.totalPages)}</span><button class="btn btn-secondary btn-sm" type="button" data-quiz-notebook-page="${page + 1}" ${page + 1 >= data.totalPages ? 'disabled' : ''}>Mai vechi</button></nav>`
      : html`<p class="muted" role="status">${result === 'ALL' && !category ? 'Caietul este gol. Răspunde la quiz-ul zilei și explicația va apărea aici.' : 'Nu ai răspunsuri care să corespundă filtrelor alese.'}</p>`}
    </section>`);
  }

  async function refresh({ reset = false } = {}) {
    if (!active()) return;
    if (reset) page = 0;
    const request = ++generation;
    root.setAttribute('aria-busy', 'true');
    try {
      const response = await get('/gamification/quiz/history' + query({ page, size: 10, result, category }), { signal });
      if (!active() || generation !== request) return;
      data = response;
      page = data.page;
      render();
    } catch (err) {
      if (!active() || generation !== request || isAbort(err)) return;
      mount(root, html`<section class="card card-pad stack quiz-notebook">${header()}<p>Caietul nu a putut fi încărcat.</p><div><button class="btn btn-secondary" type="button" data-quiz-notebook-retry>Reîncearcă</button></div></section>`);
    } finally { if (generation === request) root.removeAttribute('aria-busy'); }
  }

  async function review(day) {
    try {
      const attempt = await get(`/gamification/quiz/history/${enc(day)}`, { signal });
      if (!active()) return;
      let practicing = false;
      let picked = attempt.selectedIndex;
      const content = () => reviewContent(attempt, practicing, picked);
      await modal({
        title: `Quiz · ${date(attempt.date)}`,
        content: html`<div data-quiz-review-body>${content()}</div>`,
        footer: html`<button class="btn btn-primary" type="button" data-quiz-practice>Testează-mă din nou</button>`,
        onMount: (dlg, close) => {
          dialogs.add(close);
          dlg.addEventListener('close', () => dialogs.delete(close), { once: true });
          const body = qs('[data-quiz-review-body]', dlg);
          qs('[data-quiz-practice]', dlg).addEventListener('click', () => {
            if (!active()) return;
            practicing = true;
            picked = null;
            mount(body, content());
            qs('[data-quiz-review-answer]', body)?.focus();
          });
          on(body, 'click', '[data-quiz-review-answer]', (e, button) => {
            if (!active() || !practicing || picked !== null) return;
            picked = Number(button.dataset.quizReviewAnswer);
            mount(body, content());
            qs('[data-quiz-review-feedback]', body)?.focus();
          });
        },
      });
    } catch (err) { if (active() && !isAbort(err)) handleError(err); }
  }

  const offs = [
    on(root, 'change', '[data-quiz-result-filter]', (e, select) => { result = select.value; refresh({ reset: true }); }),
    on(root, 'change', '[data-quiz-category-filter]', (e, select) => { category = select.value; refresh({ reset: true }); }),
    on(root, 'click', '[data-quiz-notebook-retry]', () => refresh()),
    on(root, 'click', '[data-quiz-notebook-page]', (e, b) => busy(b, async () => { page = Math.max(0, Number(b.dataset.quizNotebookPage)); await refresh(); })),
    on(root, 'click', '[data-quiz-review]', (e, b) => busy(b, () => review(b.dataset.quizReview))),
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

function reviewContent(attempt, practicing, picked) {
  const done = picked !== null;
  const correct = picked === attempt.correctIndex;
  return html`<div class="stack"><span class="tag tag-brand">${attempt.category}</span>
    <p class="quiz-q">${attempt.question}</p>
    <p class="small muted">${practicing ? 'Exercițiu de recapitulare · fără AP sau modificări ale rezultatului salvat.' : html`Răspuns salvat · ${apAmount(attempt.rewardPoints)} din quiz`}</p>
    <div class="quiz-opts" role="group" aria-label="Variante pentru recapitulare">${join(attempt.options, (option, i) => html`<button class="quiz-opt ${done && i === attempt.correctIndex ? 'is-correct' : ''} ${done && picked === i && !correct ? 'is-wrong' : ''}" type="button" data-quiz-review-answer="${i}" aria-pressed="${String(picked === i)}" ${done ? 'disabled' : ''}>
      <span class="k">${String.fromCharCode(65 + i)}</span><span>${option}${done && picked === i ? html`<span class="small strong quiz-option-note">${practicing ? 'Alegerea ta' : 'Răspunsul tău'}</span>` : ''}${done && i === attempt.correctIndex ? html`<span class="small strong quiz-option-note">Răspuns corect</span>` : ''}</span>
    </button>`)}</div>
    ${done ? html`<div class="callout ${correct ? 'is-success' : 'is-warning'}" role="status" tabindex="-1" data-quiz-review-feedback><div><strong>${correct ? 'Corect.' : 'Revezi explicația.'}</strong><p>${attempt.explanation}</p></div></div>` : html`<p class="small muted" role="status">Alege un răspuns ca să verifici ce ai reținut.</p>`}
  </div>`;
}
