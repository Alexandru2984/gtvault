import { html, mount, join, productImage, safeHref, qs, on } from '../dom.js';
import { get, post } from '../api.js';
import { icon, coin } from '../icons.js';
import { state, isAuthed } from '../store.js';
import { num, timeAgo, plural } from '../format.js';
import { productGrid, skeletonGrid, apAmount, avatar, countdownEl, progress } from '../components.js';
import { setMeta } from '../seo.js';
import { busy, celebrate, handleError, toastOk } from '../ui.js';
import { savingGoal } from '../saving-goal.js';

const safe = (p) => p.catch(() => null);

export default async function home({ root, signal, alive }) {
  setMeta({});
  const authed = isAuthed();
  mount(root, html`<div class="page">
    ${authed ? html`<div class="welcome"><div class="skel skel-block"></div><div class="skel skel-block"></div></div>` : heroSkeleton()}
    ${skeletonGrid(4)}
  </div>`);

  const opts = { signal };
  const [stats, deals, catalog, feed, status, wordle, quests] = await Promise.all([
    safe(get('/community/stats', opts)),
    safe(get('/flash-deals', opts)),
    safe(get('/rewards?sort=featured', opts)),
    safe(get('/community/activity?limit=8', opts)),
    authed ? safe(get('/gamification/status', opts)) : null,
    safe(get('/games/wordle', opts)),
    authed ? safe(get('/quests', opts)) : null,
  ]);
  if (!alive()) return;

  const products = (catalog?.rewards || []).slice(0, 8);
  mount(root, html`<div class="page">
    ${authed && status ? dashboard(status, wordle, quests) : hero(stats, products)}

    ${authed ? html`<div data-saving-goal></div>` : ''}

    ${deals?.length ? html`<section class="section" aria-labelledby="deals-h">
      <div class="section-head">
        <h2 id="deals-h">⚡ Oferte flash</h2>
        <a href="/magazin?oferte=1">Toate ofertele ${icon('chevronRight')}</a>
      </div>
      <div class="scroller">${join(deals, dealCard)}</div>
    </section>` : ''}

    <section class="section" aria-labelledby="top-h">
      <div class="section-head">
        <h2 id="top-h">Premii recomandate</h2>
        <a href="/magazin">Vezi tot magazinul ${icon('chevronRight')}</a>
      </div>
      ${products.length ? productGrid(products) : html`<p class="muted">Catalogul se încarcă în curând.</p>`}
    </section>

    <div class="grid-2">
      <section class="card card-pad" aria-labelledby="feed-h">
        <div class="card-head"><h2 class="card-title" id="feed-h">${icon('sparkles')} Se întâmplă acum</h2><a class="small strong" href="/comunitate">Comunitate</a></div>
        ${feed?.length ? html`<ul class="feed">${join(feed, feedItem)}</ul>` : html`<p class="muted">Fii primul care face valuri azi! 🌊</p>`}
      </section>
      <section class="card card-pad stack" aria-labelledby="wd-h">
        <div class="card-head"><h2 class="card-title" id="wd-h">🔤 Tech Wordle #${wordle?.puzzleNumber || 1}</h2>${wordle ? countdownEl(wordle.resetsAt, { prefix: 'Nou în' }) : ''}</div>
        <p class="muted">Ghicește cuvântul tech al zilei din 6 încercări. Cu cât ghicești mai repede, cu atât câștigi mai mult — până la ${apAmount(wordle?.rewards?.[0] || 150)}.</p>
        ${wordleMini(wordle)}
        <div class="row">
          <a class="btn btn-primary" href="/wordle">${wordle?.status === 'PLAYING' ? (wordle?.guesses?.length ? 'Continuă jocul' : 'Joacă acum') : 'Vezi rezultatul'}</a>
          <span class="small muted">${wordle?.community ? `${plural(wordle.community.solvers, 'membru a', 'membri au')} rezolvat azi` : ''}</span>
        </div>
      </section>
    </div>

    ${!authed ? howItWorks() : ''}
  </div>`);

  const offClaim = on(root, 'click', '[data-claim-daily]', (e, btn) => busy(btn, async () => {
    try {
      const r = await post('/gamification/daily-claim');
      toastOk(r.message, { icon: '🔥' });
      await celebrate(r, { confettiOn: r.milestoneBonus > 0 });
      const li = btn.closest('.todo');
      li?.classList.add('is-done');
      btn.remove();
      const meta = li?.querySelector('.todo-meta');
      if (meta) meta.textContent = `Streak: ${plural(r.streakDays, 'zi', 'zile')} 🔥`;
    } catch (err) { handleError(err); }
  }));
  const goalReady = authed ? savingGoal(qs('[data-saving-goal]', root), { signal, alive }) : null;
  return () => { offClaim(); goalReady?.then((cleanup) => cleanup()); };
}

function heroSkeleton() {
  return html`<div class="hero"><div class="skel skel-title"></div></div>`;
}

function hero(stats, products) {
  const art = products.filter((p) => p.image).slice(0, 6);
  return html`<section class="hero reveal" aria-labelledby="hero-h">
    <div class="hero-grid">
      <div>
        <span class="hero-badge">🎁 +1.000 AP bonus de bun venit</span>
        <h1 id="hero-h">Învață tech. Câștigă AP. Ia premii reale.</h1>
        <p>Quiz zilnic, Tech Wordle, misiuni, bresle și un streak care te ține în formă. Punctele le transformi în tricouri, cursuri Udemy, mentorat 1:1 și gadgeturi.</p>
        <div class="hero-cta">
          <a class="btn btn-gold btn-lg" href="/inregistrare">Creează cont gratuit</a>
          <a class="btn btn-ghost btn-lg" href="/magazin">Vezi premiile</a>
        </div>
        ${stats ? html`<div class="stats-strip mt-3">
          <div class="stat"><div class="stat-value">${num(stats.members)}</div><div class="stat-label">membri</div></div>
          <div class="stat"><div class="stat-value">${num(stats.activeToday)}</div><div class="stat-label">activi azi</div></div>
          <div class="stat"><div class="stat-value">${num(stats.ordersThisWeek)}</div><div class="stat-label">premii revendicate săptămâna asta</div></div>
          <div class="stat"><div class="stat-value">${num(stats.xpEarnedThisWeek)}</div><div class="stat-label">XP câștigat săptămâna asta</div></div>
        </div>` : ''}
      </div>
      ${art.length ? html`<div class="hero-art" aria-hidden="true">${join(art, (p) => html`<img src="${productImage(p)}" alt="" width="120" height="120">`)}</div>` : ''}
    </div>
  </section>`;
}

function dashboard(s, wordle, quests) {
  const u = state.user || {};
  const t = s.tier || {};
  const todos = [
    {
      done: !s.canClaimDaily, icon: '🔥', title: 'Bonusul zilnic de streak',
      meta: s.canClaimDaily ? `+${num(s.nextDailyBonus)} AP azi · streak ${plural(s.streakDays, 'zi', 'zile')}` : `Streak: ${plural(s.streakDays, 'zi', 'zile')} 🔥`,
      action: s.canClaimDaily ? html`<button class="btn btn-gold btn-sm" type="button" data-claim-daily>Revendică</button>` : null,
      href: null,
    },
    { done: !s.canPlayQuiz, icon: '🧠', title: 'Quiz-ul tehnic al zilei', meta: s.canPlayQuiz ? 'Un răspuns corect = AP' : 'Rezolvat azi', href: '/activitati#quiz' },
    { done: wordle?.status && wordle.status !== 'PLAYING', icon: '🔤', title: 'Tech Wordle', meta: wordle?.status === 'WON' ? 'Ghicit! 🎉' : wordle?.status === 'LOST' ? 'Mâine e o nouă șansă' : `${wordle?.guessesLeft ?? 6} încercări rămase`, href: '/wordle' },
    { done: !s.freeSpinAvailable, icon: '🎡', title: 'Rotirea gratuită a roții', meta: s.freeSpinAvailable ? 'Gratuit o dată pe zi' : 'Folosită azi', href: '/activitati#roata' },
    { done: !s.canOpenFortune, icon: '🥠', title: 'Răvașul zilei', meta: s.canOpenFortune ? 'Un sfat tech + AP' : 'Deschis azi', href: '/activitati#ravas' },
  ];
  if (quests?.claimable) {
    todos.push({ done: false, icon: '🎯', title: 'Misiuni de revendicat', meta: `${plural(quests.claimable, 'recompensă te așteaptă', 'recompense te așteaptă')}`, href: '/activitati#misiuni' });
  }
  const left = todos.filter((x) => !x.done).length;
  return html`<div class="welcome reveal">
    <section class="card card-pad balance-card stack" aria-labelledby="bal-h">
      <div class="row-between">
        <h1 id="bal-h">Salut, ${(u.name || '').split(' ')[0] || 'coder'}! 👋</h1>
        <span class="tag tag-gold">${t.icon || '🏅'} ${t.title || ''}</span>
      </div>
      <div>
        <div class="muted small">Soldul tău</div>
        <div class="balance-big">${coin()}${num(s.activityPoints)} <span class="small muted">AP</span></div>
      </div>
      ${t.nextTier ? html`<div class="stack stack-sm">
        <div class="row-between small"><span>${num(t.currentXp)} XP</span><span class="muted">încă ${num(t.xpToNext)} XP până la ${t.nextTier}</span></div>
        ${progress(t.progressPercent || 0, '', 'Progres spre nivelul următor')}
      </div>` : html`<p class="small">Ai atins nivelul maxim. Respect! 💎</p>`}
      <div class="row">
        <a class="btn btn-gold" href="/magazin">${icon('bag')} Cheltuie AP</a>
        <a class="btn btn-ghost" href="/activitati">${icon('play')} Câștigă AP</a>
      </div>
    </section>
    <section class="card card-pad stack" aria-labelledby="today-h">
      <div class="card-head">
        <h2 class="card-title" id="today-h">${icon('calendar')} Azi te așteaptă</h2>
        ${countdownEl(s.resetsAt, { prefix: 'Reset în' })}
      </div>
      <p class="small muted">${left ? `${plural(left, 'activitate disponibilă', 'activități disponibile')}. Hai că se adună!` : 'Ai bifat tot azi. Ești o mașină! 🏆'}</p>
      <ul class="todo-list">${join(todos, todo)}</ul>
    </section>
  </div>`;
}

function todo(t) {
  const inner = html`<span class="todo-icon" aria-hidden="true">${t.icon}</span>
    <span class="grow"><span class="todo-title">${t.title}</span><br><span class="todo-meta">${t.meta}</span></span>
    ${t.action || html`<span class="todo-check" aria-label="${t.done ? 'Făcut' : 'De făcut'}">${t.done ? icon('check') : ''}</span>`}`;
  return t.href
    ? html`<li><a class="todo ${t.done ? 'is-done' : ''}" href="${t.href}">${inner}</a></li>`
    : html`<li class="todo ${t.done ? 'is-done' : ''}">${inner}</li>`;
}

function dealCard(d) {
  return html`<a class="deal" href="/produs/${encodeURIComponent(d.rewardId)}">
    <img src="${productImage(d)}" alt="" width="76" height="76" loading="lazy">
    <div class="deal-body">
      <span class="tag tag-hot">−${d.discountPercent}%</span>
      <span class="deal-name ellipsis">${d.name}</span>
      <span class="row"><span class="price-now is-deal">${coin()}${num(d.dealPrice)}</span><span class="price-old">${num(d.normalPrice)}</span></span>
      ${progress(d.claimedPercent || 0, 'progress-sm is-gold', 'Procent revendicat')}
      <span class="row-between xsmall"><span class="muted">${d.remaining} rămase</span>${countdownEl(d.endsAt)}</span>
    </div>
  </a>`;
}

export function feedItem(f) {
  const inner = html`${f.actor ? avatar(f.actor.avatar, f.actor.name) : html`<span class="feed-icon" aria-hidden="true">${f.icon}</span>`}
    <div><div class="feed-title">${f.actor ? html`${f.icon} ` : ''}${f.title}</div>
    ${f.detail ? html`<div class="feed-detail">${f.detail}</div>` : ''}
    <div class="feed-at">${timeAgo(f.at)}</div></div>`;
  return f.link ? html`<li><a class="feed-item" href="${safeHref(f.link)}">${inner}</a></li>` : html`<li class="feed-item">${inner}</li>`;
}

function wordleMini(w) {
  if (!w) return '';
  const rows = (w.guesses || []).slice(-3);
  if (!rows.length) {
    return html`<div class="wd-row" aria-hidden="true">${join(['T', 'E', 'C', 'H', '?'], (ch, i) => html`<div class="wd-tile" data-state="${i === 4 ? 'NEAR' : i < 2 ? 'HIT' : 'MISS'}">${ch}</div>`)}</div>`;
  }
  return html`<div class="stack stack-sm" aria-label="Încercările tale de azi">${join(rows, (g) => html`<div class="wd-row">${join([...g.word], (ch, i) => html`<div class="wd-tile" data-state="${g.marks[i]}">${ch}</div>`)}</div>`)}</div>`;
}

function howItWorks() {
  return html`<section class="section" aria-labelledby="how-h">
    <div class="section-head"><h2 id="how-h">Cum funcționează</h2><a href="/ajutor">Întrebări frecvente ${icon('chevronRight')}</a></div>
    <div class="steps">
      <div class="card step"><h3>Intră în comunitate</h3><p>Îți faci cont în 20 de secunde și primești 1.000 AP bonus. Alegi o breaslă: Java, Python, JavaScript sau DevOps.</p></div>
      <div class="card step"><h3>Câștigă AP zilnic</h3><p>Streak, quiz tehnic, Tech Wordle, roata norocului, misiuni săptămânale, recenzii și invitații pentru prieteni.</p></div>
      <div class="card step"><h3>Revendică premii</h3><p>Tricouri și stickere livrate acasă, vouchere digitale instant, mentorat 1:1 și acces la cursuri.</p></div>
    </div>
  </section>`;
}
