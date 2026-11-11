import { html, mount, join, vars } from '../dom.js';
import { get, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, num, plural } from '../format.js';
import { avatar, emptyState, skeletonPage, stars } from '../components.js';
import { setMeta } from '../seo.js';

const HEX = /^#[0-9a-fA-F]{3,8}$/;

export default async function member({ root, params, signal, alive }) {
  mount(root, skeletonPage());
  let p;
  try {
    p = await get(`/users/${enc(params.id)}`, { signal });
  } catch (err) {
    if (isAbort(err)) return;
    if (err.status === 404 || err.status === 400) {
      setMeta({ title: 'Membru indisponibil', noindex: true });
      mount(root, emptyState({ icon: '👻', title: 'Membrul nu a fost găsit', text: 'Contul nu există sau a fost șters.', action: html`<a class="btn btn-primary" href="/clasament">Vezi clasamentul</a>` }));
      return;
    }
    throw err;
  }
  if (!alive()) return;
  setMeta({ title: p.name, description: `${p.name} în comunitatea Generația Tech${p.tier ? ` · ${p.tier.title}` : ''}.`, noindex: true });

  if (p.private) {
    mount(root, html`<div class="page">
      <section class="card member-hero">
        ${avatar(p.avatar, p.name, 'xl')}
        <div class="stack stack-sm"><h1>${p.name}</h1>${p.tier ? html`<span class="tag tag-brand">${p.tier.icon} ${p.tier.title}</span>` : ''}
          <p class="muted">${icon('lock')} Acest membru și-a setat profilul ca privat.</p></div>
      </section>
    </div>`);
    return;
  }

  const g = p.guild;
  mount(root, html`<div class="page">
    <section class="card member-hero">
      ${avatar(p.avatar, p.name, 'xl')}
      <div class="stack stack-sm">
        <div class="row"><h1>${p.name}</h1>${p.isMe ? html`<a class="btn btn-ghost btn-sm" href="/profil">${icon('edit')} Editează profilul</a>` : ''}</div>
        <div class="row">
          ${p.tier ? html`<span class="tag tag-brand">${p.tier.icon} ${p.tier.title}</span>` : ''}
          ${g ? html`<a class="tag" href="/bresle" data-vars="${vars({ '--c': HEX.test(g.color) ? g.color : null })}">${g.icon} ${g.name}</a>` : ''}
          <span class="tag">Membru din ${date(p.memberSince)}</span>
        </div>
        ${p.bio ? html`<p class="prose">${p.bio}</p>` : ''}
      </div>
    </section>
    <div class="kpis">
      <div class="kpi"><span class="kpi-label">XP total</span><span class="kpi-value">${num(p.xp)}</span><span class="kpi-sub">locul ${num(p.xpRank)} în comunitate</span></div>
      <div class="kpi"><span class="kpi-label">Streak</span><span class="kpi-value">🔥 ${num(p.streakDays)}</span><span class="kpi-sub">record ${plural(p.longestStreak, 'zi', 'zile')}</span></div>
      <div class="kpi"><span class="kpi-label">Insigne</span><span class="kpi-value">🏅 ${num(p.badgeCount)}</span><span class="kpi-sub">deblocate</span></div>
      <div class="kpi"><span class="kpi-label">Tech Wordle</span><span class="kpi-value">${num(p.wordle?.wins || 0)}</span><span class="kpi-sub">câștigate din ${num(p.wordle?.played || 0)} · serie max ${num(p.wordle?.maxStreak || 0)}</span></div>
    </div>
    <section class="card card-pad stack" aria-labelledby="b-h">
      <h2 class="card-title" id="b-h">🏅 Insigne</h2>
      ${p.badges?.length ? html`<div class="badges-grid">${join(p.badges, (b) => html`<div class="badge-tile" data-rarity="${b.rarity}">
        <span class="badge-ico" aria-hidden="true">${b.icon}</span><span class="badge-name">${b.title}</span>
        <span class="rarity">${b.rarity}</span><span class="badge-desc">${date(b.unlockedAt)}</span></div>`)}</div>`
        : html`<p class="muted">Nicio insignă încă.</p>`}
    </section>
    <section class="card card-pad stack" aria-labelledby="r-h">
      <h2 class="card-title" id="r-h">${icon('message')} Recenzii (${num(p.reviewCount)})</h2>
      ${p.recentReviews?.length ? join(p.recentReviews, (r) => html`<article class="review">
        <div class="row-between"><a class="strong" href="/produs/${enc(r.rewardId)}">${r.rewardName}</a><span class="small muted">${date(r.createdAt)}</span></div>
        ${stars(r.rating)}<p class="review-body">${r.comment}</p></article>`)
        : html`<p class="muted">Nicio recenzie publicată.</p>`}
    </section>
  </div>`);
}
