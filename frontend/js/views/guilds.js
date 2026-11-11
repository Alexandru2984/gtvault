import { html, mount, join, on, vars } from '../dom.js';
import { get, post, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, num, plural } from '../format.js';
import { avatar, countdownEl, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { isAuthed } from '../store.js';
import { busy, confirmDialog, handleError, toastOk } from '../ui.js';

const HEX = /^#[0-9a-fA-F]{3,8}$/;

export default async function guilds({ root, signal, alive }) {
  setMeta({ title: 'Bresle', description: 'Cavalerii Java, Vrăjitorii Python și celelalte bresle Generația Tech se întrec săptămânal în XP. Alege-ți tabăra!' });
  mount(root, skeletonPage());
  let data;
  let mine = null;
  async function load() {
    [data, mine] = await Promise.all([
      get('/guilds', { signal }),
      isAuthed() ? get('/guilds/me', { signal }).catch(() => null) : null,
    ]);
  }
  try {
    await load();
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  function render() {
    const list = data.guilds || [];
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Bresle</h1><p>Tot XP-ul câștigat de membri contează pentru breaslă. Clasamentul săptămânal se resetează în ${countdownEl(data.weekEndsAt)}.</p></div>
        <a class="btn btn-ghost btn-sm" href="/clasament">${icon('trophy')} Clasament individual</a>
      </div>
      ${mine && !mine.canSwitch && mine.canSwitchAt ? html`<div class="callout">${icon('info')}<span>Ești în <strong>${mine.icon} ${mine.name}</strong>. Poți schimba breasla după ${date(mine.canSwitchAt)}.</span></div>` : ''}
      <div class="grid-2">${join(list, (g) => {
        const isMine = mine?.id === g.id;
        return html`<article class="card card-pad guild-card ${isMine ? 'is-mine' : ''}" data-vars="${vars({ '--c': HEX.test(g.color) ? g.color : null })}">
          <div class="row row-nowrap">
            <span class="guild-icon" aria-hidden="true">${g.icon}</span>
            <div class="grow"><h2 class="card-title">${g.name}</h2><span class="small muted">Locul ${g.rank} săptămâna asta</span></div>
            ${g.rank === 1 ? html`<span class="tag tag-gold">👑 Lider</span>` : ''}
          </div>
          <p class="small muted">${g.description}</p>
          <div class="guild-stats">
            <div><strong>${num(g.weeklyXp)}</strong><span>XP săptămâna asta</span></div>
            <div><strong>${num(g.members)}</strong><span>${g.members === 1 ? 'membru' : 'membri'}</span></div>
            <div><strong>${num(g.totalXp)}</strong><span>XP total</span></div>
          </div>
          ${g.topMembers?.length ? html`<div class="row-between">
            <div class="avatars">${join(g.topMembers.slice(0, 5), (m) => html`<a href="/membru/${encodeURIComponent(m.id)}" title="${m.name} · ${num(m.xp)} XP">${avatar(m.avatar, m.name, 'sm')}</a>`)}</div>
            <span class="xsmall muted">Top: ${g.topMembers[0].name}</span>
          </div>` : html`<p class="xsmall muted">${plural(0, 'membru', 'membri')} încă — fii primul!</p>`}
          ${isMine ? html`<span class="tag tag-success">${icon('check')} Breasla ta</span>`
            : isAuthed() ? html`<button class="btn btn-secondary" type="button" data-join="${g.id}" data-name="${g.name}" ${mine && !mine.canSwitch ? 'disabled' : ''}>Intră în breaslă</button>`
            : html`<a class="btn btn-secondary" href="/inregistrare">Alătură-te</a>`}
        </article>`;
      })}</div>
    </div>`);
  }
  render();

  return on(root, 'click', '[data-join]', async (e, b) => {
    const ok = await confirmDialog({ title: `Te muți în ${b.dataset.name}?`, body: 'XP-ul tău de acum înainte contează pentru noua breaslă. Poți schimba din nou peste 7 zile.', confirmText: 'Mă mut' });
    if (!ok) return;
    await busy(b, async () => {
      try {
        const r = await post('/guilds/select', { guildId: b.dataset.join });
        toastOk(r.message, { icon: r.guild?.icon || '🛡️' });
        await load();
        if (alive()) render();
      } catch (err) { handleError(err); }
    });
  });
}
