import { html, mount, join, on, productImage } from './dom.js';
import { get, del, enc, isAbort } from './api.js';
import { state, on as onStore, emit } from './store.js';
import { icon } from './icons.js';
import { apAmount, progress } from './components.js';
import { busy, confirmDialog, handleError, toastOk } from './ui.js';

export async function savingGoal(root, { signal, alive }) {
  const token = state.token;
  let disposed = false;
  let generation = 0;
  let lastBalance = state.user?.activityPoints;
  const active = () => !disposed && alive() && state.token === token;

  async function load() {
    if (!active()) return;
    const request = ++generation;
    try {
      const data = await get('/user/saving-goal', { signal });
      if (!active() || request !== generation) return;
      mount(root, goalCard(data.goal));
    } catch (err) {
      if (!active() || isAbort(err) || request !== generation) return;
      mount(root, html`<section class="card card-pad stack"><h2 class="card-title">🎯 Obiectivul meu</h2><p class="small muted">Obiectivul nu a putut fi încărcat.</p><div><button class="btn btn-secondary" type="button" data-goal-retry>Reîncearcă</button></div></section>`);
    }
  }

  const offs = [
    onStore('user', (user) => {
      if (user?.activityPoints === lastBalance) return;
      lastBalance = user?.activityPoints;
      load();
    }),
    onStore('goal:changed', () => load()),
    on(root, 'click', '[data-goal-retry]', () => load()),
    on(root, 'click', '[data-goal-clear]', async (e, b) => {
      if (!(await confirmDialog({ title: 'Elimini obiectivul?', body: 'Poți alege oricând un alt premiu din magazin.', confirmText: 'Elimină obiectivul' }))) return;
      if (!active()) return;
      await busy(b, async () => {
        try {
          await del('/user/saving-goal');
          if (!active()) return;
          toastOk('Obiectiv eliminat.');
          emit('goal:changed');
        } catch (err) { if (active()) handleError(err); }
      });
    }),
  ];
  await load();
  return () => { disposed = true; generation++; offs.forEach((off) => off()); };
}

export function goalCard(goal) {
  if (!goal) return html`<section class="card card-pad stack saving-goal" aria-label="Obiectivul meu">
    <h2 class="card-title">🎯 Obiectivul meu</h2>
    <p class="muted">Alege un premiu care să te motiveze. Urmărești aici progresul AP, pe toate dispozitivele.</p>
    <div><a class="btn btn-secondary" href="/magazin">Alege un premiu țintă</a></div>
  </section>`;
  const r = goal.reward;
  return html`<section class="card card-pad stack saving-goal" aria-label="Obiectivul meu" data-goal-status="${goal.status}">
    <div class="row-between"><h2 class="card-title">🎯 Obiectivul meu</h2><div class="row"><button class="btn btn-ghost btn-sm" type="button" data-goal-retry aria-label="Actualizează progresul obiectivului">Actualizează</button><button class="btn btn-ghost btn-sm" type="button" data-goal-clear>${icon('x')} Elimină</button></div></div>
    <div class="goal-summary">
      <img src="${productImage(r)}" alt="" width="88" height="88" loading="lazy">
      <div class="stack stack-sm grow">
        <h3>${r.name}</h3>
        ${goal.targetPrice == null ? html`<p class="muted">Premiul a fost retras. Alege un nou obiectiv din magazin.</p>` : html`
          <p class="small">Sold ${apAmount(goal.balance)} · țintă ${apAmount(goal.targetPrice)}</p>
          ${progress(goal.progressPercent, '', 'Progresul AP pentru premiul țintă')}
          <p class="small strong" role="status">${goal.ready ? 'Ai AP suficiente și premiul este disponibil!'
            : goal.missingPoints > 0 ? html`Mai ai nevoie de ${apAmount(goal.missingPoints)}.` : 'Ai AP suficiente. Verifică mai jos condițiile premiului.'}</p>
          ${goal.flashDiscount || goal.tierDiscount ? html`<p class="small muted">Estimarea include ${goal.flashDiscount ? html`reducerea flash de ${apAmount(goal.flashDiscount)}` : ''}${goal.flashDiscount && goal.tierDiscount ? ' și ' : ''}${goal.tierDiscount ? html`reducerea de nivel de ${apAmount(goal.tierDiscount)}` : ''}.</p>` : ''}
        `}
      </div>
    </div>
    ${r.active && goal.issues?.length ? html`<div class="callout is-warning"><div>${join(goal.issues, (issue) => html`<p>${issue}</p>`)}</div></div>` : ''}
    <p class="xsmall muted">Total estimat pentru o bucată, fără cod promo. AP și stocul rămân disponibile; prețul se confirmă la finalizarea comenzii. Primești o singură alertă pentru obiectivul ales, verificată periodic și controlată din preferințele de progres.</p>
    <div class="row">
      ${r.active ? html`<a class="btn ${goal.ready ? 'btn-primary' : 'btn-secondary'}" href="/produs/${enc(r.id)}">${goal.ready ? 'Vezi premiul și comandă' : 'Vezi premiul'}</a>` : ''}
      <a class="btn btn-ghost" href="/magazin">Schimbă premiul</a>
    </div>
  </section>`;
}
