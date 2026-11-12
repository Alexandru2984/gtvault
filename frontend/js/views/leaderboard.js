import { html, mount, join, on } from '../dom.js';
import { get, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { num } from '../format.js';
import { avatar, emptyState, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { setQuery } from '../router.js';
import { isAuthed } from '../store.js';
import { handleError } from '../ui.js';

const PERIODS = { week: 'Săptămâna asta', month: 'Luna asta', all: 'Din totdeauna', streak: 'Streak' };

export default async function leaderboard({ root, query, signal, alive }) {
  setMeta({ title: 'Clasament', description: 'Cei mai activi membri ai comunității Generația Tech: clasament săptămânal, lunar, general și după streak.' });
  let period = PERIODS[query.p] ? query.p : 'week';
  mount(root, skeletonPage());

  async function load() {
    try {
      const d = await get(`/leaderboard?period=${period}&limit=50`, { signal });
      if (!alive()) return;
      render(d);
    } catch (err) {
      if (!isAbort(err)) handleError(err);
    }
  }

  function render(d) {
    const entries = d.entries || [];
    const unit = period === 'streak' ? 'zile' : 'XP';
    const top = entries.slice(0, 3);
    const rest = entries.slice(3);
    const meInList = entries.some((e) => e.isMe);
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Clasament</h1><p>${period === 'streak' ? 'Cele mai lungi streak-uri active.' : 'XP câștigat din activitate. XP-ul nu se cheltuie, deci clasamentul arată efortul real.'}</p></div>
        <a class="btn btn-secondary btn-sm" href="/bresle">${icon('shield')} Clasamentul breslelor</a>
      </div>
      <div class="tabs" role="tablist" aria-label="Perioada">
        ${join(Object.entries(PERIODS), ([k, v]) => html`<button type="button" role="tab" data-period="${k}" aria-selected="${String(k === period)}">${v}</button>`)}
      </div>
      ${entries.length ? html`
        <div class="card card-pad">
          <div class="podium">${join([top[1], top[0], top[2]], (e, i) => e ? html`<a class="podium-place p${[2, 1, 3][i]}" href="/membru/${encodeURIComponent(e.id)}">
            ${avatar(e.avatar, e.name, 'lg')}
            <span class="podium-name ellipsis">${e.name}${e.isMe ? ' (tu)' : ''}</span>
            <span class="small muted">${e.tierIcon} ${num(e.value)} ${unit}</span>
            <span class="podium-block">${[2, 1, 3][i]}</span>
          </a>` : html`<span></span>`)}</div>
        </div>
        ${rest.length ? html`<ol class="card lb-list">${join(rest, (e) => row(e, unit))}</ol>` : ''}
        ${d.me && !meInList ? html`<div class="me-sticky card"><ol class="lb-list">${row(d.me, unit)}</ol></div>` : ''}
      ` : emptyState({ icon: '🏁', title: 'Clasamentul e gol deocamdată', text: 'Primul care câștigă XP în perioada asta ajunge pe podium!', action: html`<a class="btn btn-primary" href="/activitati">Câștigă XP</a>` })}
      ${!isAuthed() ? html`<p class="small muted center"><a href="/inregistrare">Creează-ți cont</a> ca să apari în clasament.</p>` : ''}
    </div>`);
  }

  function row(e, unit) {
    return html`<li><a class="lb-row ${e.isMe ? 'is-me' : ''}" href="/membru/${encodeURIComponent(e.id)}">
      <span class="lb-rank">${e.rank}</span>
      ${avatar(e.avatar, e.name)}
      <span class="lb-name"><span class="ellipsis">${e.name}</span>${e.isMe ? html`<span class="tag tag-brand">tu</span>` : ''}<span title="${e.tier}">${e.tierIcon}</span></span>
      <span class="lb-value">${num(e.value)} <span class="small muted">${unit}</span></span>
    </a></li>`;
  }

  await load();
  return on(root, 'click', '[data-period]', (e, b) => {
    period = b.dataset.period;
    setQuery({ p: period === 'week' ? '' : period });
    for (const t of root.querySelectorAll('[data-period]')) t.setAttribute('aria-selected', String(t === b));
    load();
  });
}

