import { html, mount, join, qs, on, vars } from '../dom.js';
import { get, post, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { num, plural, percent } from '../format.js';
import { apAmount, countdownEl, loginPrompt, progress, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { isAuthed } from '../store.js';
import { busy, celebrate, confirmDialog, handleError, toast, toastOk } from '../ui.js';

const HEX = /^#[0-9a-fA-F]{3,8}$/;
const safeColor = (c, fallback = '#7c3aed') => (HEX.test(c || '') ? c : fallback);
const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default async function activities({ root, signal, alive }) {
  setMeta({
    title: 'Activități zilnice',
    description: 'Câștigă AP zilnic: streak, quiz tehnic, răvașul programatorului, roata norocului, cufere misterioase și misiuni săptămânale.',
  });
  mount(root, skeletonPage());
  const authed = isAuthed();
  const opt = { signal };
  let config;
  let status = null;
  let quiz = null;
  let quests = null;
  try {
    [config, status, quiz, quests] = await Promise.all([
      get('/gamification/config', opt),
      authed ? get('/gamification/status', opt) : null,
      get('/gamification/quiz', opt).catch(() => null),
      authed ? get('/quests', opt).catch(() => null) : null,
    ]);
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  let wheelRotation = 0;
  let fortune = null;
  let crateResult = null;
  let quizResult = null;
  let dayRollover = false;

  function render() {
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Activități</h1><p>Câștigă AP în fiecare zi. Totul se resetează la miezul nopții (ora României).</p></div>
        ${status ? countdownEl(status.resetsAt, { prefix: 'Reset în' }) : ''}
      </div>
      ${!authed ? loginPrompt('Creează-ți cont ca să câștigi AP din activități. Primești și 1.000 AP bonus de bun venit.') : ''}
      <div class="games-grid">
        ${streakCard()}
        ${questsCard()}
        ${quizCard()}
        ${fortuneCard()}
        ${wheelCard()}
        ${crateCard()}
      </div>
      <section class="grid-3">
        <a class="card card-pad card-link stack stack-sm" href="/wordle"><span class="big-emoji">🔤</span><strong>Tech Wordle</strong><span class="small muted">Până la ${num(config.wordle?.rewards?.[0] || 150)} AP pe zi pentru cuvântul tech al zilei.</span></a>
        <a class="card card-pad card-link stack stack-sm" href="/comunitate"><span class="big-emoji">💡</span><strong>Propune și votează</strong><span class="small muted">+${config.community?.proposalReward} AP pe propunere, +${config.community?.voteReward} AP pe vot (max ${config.community?.maxRewardedVotesPerDay}/zi).</span></a>
        <a class="card card-pad card-link stack stack-sm" href="/profil?tab=invita"><span class="big-emoji">🤝</span><strong>Invită prieteni</strong><span class="small muted">Tu primești 300 AP, prietenul tău 200 AP în plus la înscriere.</span></a>
      </section>
    </div>`);
    if (wheelRotation) qs('#wheel', root)?.style.setProperty('transform', `rotate(${wheelRotation}deg)`);
  }

  // ---------------------------------------------------------------- streak
  function streakCard() {
    const s = status;
    const ms = Object.entries(config.streak?.milestones || {}).map(([d, p]) => [Number(d), p]).sort((a, b) => a[0] - b[0]);
    const nextMs = s ? ms.find(([d]) => d > s.streakDays) : ms[0];
    return html`<section class="card card-pad game-card" id="streak" aria-labelledby="streak-h">
      <div class="card-head"><h2 class="card-title" id="streak-h">${icon('flame')} Streak zilnic</h2>
        ${s ? html`<span class="tag" title="Protecții de streak">🛡️ ${s.streakFreezes}/${s.maxStreakFreezes}</span>` : ''}</div>
      ${s ? html`<div class="row">
        <span class="streak-flame" aria-hidden="true">🔥</span>
        <div><div class="streak-num">${plural(s.streakDays, 'zi', 'zile')}</div><div class="small muted">Record: ${plural(s.longestStreak, 'zi', 'zile')}</div></div>
        <div class="grow"></div>
        ${s.canClaimDaily ? html`<button class="btn btn-gold" type="button" data-claim>Revendică +${num(s.nextDailyBonus)} AP</button>` : html`<span class="tag tag-success">${icon('check')} Revendicat azi</span>`}
      </div>
      ${s.streakWillReset ? html`<div class="callout is-warning">${icon('alert')}<span>Ai ratat o zi și nu ai protecție de streak: la revendicare, streak-ul o ia de la capăt.</span></div>` : ''}
      ${s.freezesWillBeUsed ? html`<div class="callout">${icon('shield')}<span>Ai ratat ${plural(s.freezesWillBeUsed, 'zi', 'zile')}: se vor folosi ${plural(s.freezesWillBeUsed, 'protecție', 'protecții')} și streak-ul rămâne intact.</span></div>` : ''}
      <div class="week" role="list" aria-label="Ciclul de 7 zile">${join(s.weekCycle, (d) => html`<div class="day ${d.claimed ? 'is-claimed' : ''} ${d.today ? 'is-today' : ''}" role="listitem">
        <span class="day-n">Ziua ${d.day}</span><span class="day-ico" aria-hidden="true">${d.claimed ? '✅' : d.day === 7 ? '🎁' : '🪙'}</span><span class="day-r">${d.reward}</span>
      </div>`)}</div>
      ${nextMs ? html`<p class="small muted">Următorul prag: <strong>${plural(nextMs[0], 'zi', 'zile')}</strong> → bonus ${apAmount(nextMs[1])}</p>` : ''}
      <div class="row-between">
        <span class="small muted">O protecție salvează streak-ul într-o zi ratată.</span>
        <button class="btn btn-secondary btn-sm" type="button" data-freeze ${s.streakFreezes >= s.maxStreakFreezes ? 'disabled' : ''}>${icon('shield')} Cumpără · ${num(s.streakFreezeCost)} AP</button>
      </div>` : html`<p class="muted">Revendică bonusul în fiecare zi: ${join(config.streak?.rewards || [], (r, i) => html`${i ? ' → ' : ''}${r}`)} AP, apoi ciclul se reia. Praguri speciale la ${join(ms, ([d, p], i) => html`${i ? ', ' : ''}${d} zile (+${num(p)} AP)`)}.</p>`}
    </section>`;
  }

  // ---------------------------------------------------------------- misiuni
  function questsCard() {
    if (!quests) {
      return html`<section class="card card-pad game-card" id="misiuni" aria-labelledby="q-h">
        <div class="card-head"><h2 class="card-title" id="q-h">${icon('trophy')} Misiuni</h2></div>
        <p class="muted">Misiuni zilnice și săptămânale cu recompense în AP: conectează-te, rezolvă quiz-ul, comandă, scrie recenzii, învârte roata…</p>
      </section>`;
    }
    const quest = (x) => html`<div class="quest ${x.claimed ? 'is-claimed' : ''}">
      <div><div class="quest-title">${x.title}</div><div class="quest-desc">${x.description}</div></div>
      ${x.claimed ? html`<span class="tag tag-success">${icon('check')} +${x.rewardPoints}</span>`
        : x.completed ? html`<button class="btn btn-gold btn-sm" type="button" data-quest="${x.id}">+${x.rewardPoints} AP</button>`
        : html`<span class="tag">${x.currentProgress}/${x.targetProgress} · +${x.rewardPoints}</span>`}
      ${!x.claimed ? progress(percent(x.currentProgress, x.targetProgress), x.completed ? 'progress-sm is-success' : 'progress-sm', x.title) : ''}
    </div>`;
    return html`<section class="card card-pad game-card" id="misiuni" aria-labelledby="q-h">
      <div class="card-head"><h2 class="card-title" id="q-h">${icon('trophy')} Misiuni</h2>${quests.claimable ? html`<span class="tag tag-gold">${quests.claimable} de revendicat</span>` : ''}</div>
      <div><div class="row-between small"><strong>Azi</strong>${countdownEl(quests.dailyResetsAt)}</div>${join(quests.daily, quest)}</div>
      <div><div class="row-between small"><strong>Săptămâna asta</strong>${countdownEl(quests.weeklyResetsAt)}</div>${join(quests.weekly, quest)}</div>
    </section>`;
  }

  // ---------------------------------------------------------------- quiz
  function quizCard() {
    if (!quiz) return '';
    const done = quiz.alreadyAnswered || quizResult;
    const correctIndex = quizResult ? quizResult.correctIndex : quiz.correctIndex;
    const wasCorrect = quizResult ? quizResult.isCorrect : quiz.wasCorrect;
    const picked = quizResult?.picked;
    const letters = ['A', 'B', 'C', 'D', 'E'];
    return html`<section class="card card-pad game-card" id="quiz" aria-labelledby="quiz-h">
      <div class="card-head"><h2 class="card-title" id="quiz-h">${icon('sparkles')} Quiz-ul zilei</h2><span class="tag tag-brand">${quiz.category}</span></div>
      <p class="quiz-q">${quiz.question}</p>
      <div class="quiz-opts" role="group" aria-label="Variante de răspuns">
        ${join(quiz.options, (o, i) => html`<button class="quiz-opt ${done && i === correctIndex ? 'is-correct' : ''} ${done && picked === i && i !== correctIndex ? 'is-wrong' : ''}" type="button" data-answer="${i}" ${done || !isAuthed() ? 'disabled' : ''}>
          <span class="k">${letters[i]}</span><span>${o}</span></button>`)}
      </div>
      ${done ? html`<div class="callout ${wasCorrect ? 'is-success' : 'is-warning'}">${icon(wasCorrect ? 'check' : 'info')}<div>
          <strong>${wasCorrect ? `Corect! +${quiz.rewardPoints} AP` : 'De data asta n-a mers.'}</strong>
          ${(quizResult?.explanation || quiz.explanation) ? html`<div class="small">${quizResult?.explanation || quiz.explanation}</div>` : ''}
          <div class="small muted">Întrebare nouă mâine.</div></div></div>`
      : html`<p class="small muted">Un singur răspuns pe zi · răspuns corect = ${apAmount(quiz.rewardPoints)}</p>`}
    </section>`;
  }

  // ---------------------------------------------------------------- ravas
  function fortuneCard() {
    const can = status?.canOpenFortune;
    return html`<section class="card card-pad game-card" id="ravas" aria-labelledby="f-h">
      <div class="card-head"><h2 class="card-title" id="f-h">🥠 Răvașul programatorului</h2><span class="tag">+${config.fortune?.reward} AP</span></div>
      <div class="fortune">
        ${fortune ? html`<p class="fortune-quote">„${fortune.quote}”</p><p class="small strong">— ${fortune.author}</p>${fortune.tip ? html`<p class="fortune-tip">💡 ${fortune.tip}</p>` : ''}`
          : html`<button class="cookie" type="button" data-fortune ${can ? '' : 'disabled'} aria-label="Deschide răvașul">🥠</button>
            <p class="small muted">${!isAuthed() ? 'Intră în cont ca să-ți deschizi răvașul.' : can ? 'Atinge prăjitura ca s-o deschizi.' : 'L-ai deschis azi. Revino mâine pentru o nouă înțelepciune.'}</p>`}
      </div>
    </section>`;
  }

  // ---------------------------------------------------------------- roata
  function wheelCard() {
    const w = config.wheel || {};
    const segs = w.segments || [];
    const free = status?.freeSpinAvailable;
    return html`<section class="card card-pad game-card" id="roata" aria-labelledby="w-h">
      <div class="card-head"><h2 class="card-title" id="w-h">🎡 Roata norocului</h2>${status ? html`<span class="tag">${free ? 'Rotire gratuită disponibilă' : `${status.paidSpinsLeft} rotiri plătite rămase`}</span>` : ''}</div>
      <div class="wheel-wrap">
        <svg class="wheel-pointer" viewBox="0 0 34 40" aria-hidden="true"><path d="M17 40 3 8a14 14 0 1 1 28 0Z" fill="#e11d48"/><circle cx="17" cy="13" r="5" fill="#fff"/></svg>
        ${wheelSvg(segs)}
        <div class="wheel-hub" aria-hidden="true">AP</div>
      </div>
      <div class="row-between">
        <span class="small muted">${free ? 'Prima rotire din zi e gratuită.' : `Rotire: ${num(w.cost)} AP · max ${w.maxPaidSpinsPerDay}/zi`}</span>
        <button class="btn ${free ? 'btn-gold' : 'btn-primary'}" type="button" data-spin ${!isAuthed() || (!free && !status?.paidSpinsLeft) ? 'disabled' : ''}>${free ? 'Învârte gratuit' : `Învârte · ${num(w.cost)} AP`}</button>
      </div>
      <details><summary class="small strong">Șanse și premii</summary>
        <div class="odds mt-1">${join(segs, (s) => html`<span class="tag" data-vars="${vars({ '--c': safeColor(s.color) })}">${s.label} · ${s.probability}%</span>`)}</div>
        <p class="xsmall muted mt-1">Valoarea medie a unei rotiri: ${num(w.expectedValue)} AP.</p>
      </details>
    </section>`;
  }

  function wheelSvg(segs) {
    const n = segs.length || 1;
    const R = 100;
    const slice = (2 * Math.PI) / n;
    const pt = (a) => [R + R * Math.sin(a), R - R * Math.cos(a)];
    const paths = segs.map((s, i) => {
      const [x1, y1] = pt(i * slice);
      const [x2, y2] = pt((i + 1) * slice);
      const mid = ((i + 0.5) * 360) / n;
      return html`<path d="M${R},${R} L${x1.toFixed(2)},${y1.toFixed(2)} A${R},${R} 0 0 1 ${x2.toFixed(2)},${y2.toFixed(2)} Z" fill="${safeColor(s.color)}" stroke="#fff" stroke-width="1.5"/>
        <text x="${R}" y="26" transform="rotate(${mid.toFixed(2)} ${R} ${R})" text-anchor="middle" font-size="11" font-weight="800" fill="#fff">${s.label}</text>`;
    });
    return html`<svg class="wheel" id="wheel" viewBox="0 0 200 200" role="img" aria-label="Roata norocului cu ${n} segmente">${paths}</svg>`;
  }

  // ---------------------------------------------------------------- cufar
  function crateCard() {
    const c = config.crate || {};
    return html`<section class="card card-pad game-card" id="cufar" aria-labelledby="c-h">
      <div class="card-head"><h2 class="card-title" id="c-h">🧰 Cufărul misterios</h2>${status ? html`<span class="tag">${status.cratesLeft} rămase azi</span>` : ''}</div>
      <div class="crate-stage" aria-live="polite">
        ${crateResult ? html`<div class="crate-result" data-vars="${vars({ '--c': safeColor(crateResult.color) })}">
          <span class="big-emoji" aria-hidden="true">${crateResult.wonPoints > c.cost ? '🤑' : crateResult.wonPoints ? '🎁' : '💨'}</span>
          <strong>+${num(crateResult.wonPoints)} AP</strong><span class="small">${crateResult.prizeName}</span></div>`
          : html`<div class="crate" id="crate" aria-hidden="true">🧰</div>`}
      </div>
      <div class="row-between">
        <span class="small muted">Cost: ${num(c.cost)} AP · max ${c.maxPerDay}/zi · medie ${num(c.expectedValue)} AP</span>
        <button class="btn btn-primary" type="button" data-crate ${!isAuthed() || !status?.cratesLeft ? 'disabled' : ''}>${crateResult ? 'Încă unul' : 'Deschide'} · ${num(c.cost)} AP</button>
      </div>
      <details><summary class="small strong">Ce poate fi înăuntru</summary>
        <div class="odds mt-1">${join(c.tiers || [], (t) => html`<span class="tag" data-vars="${vars({ '--c': safeColor(t.color) })}">${t.label} · ${num(t.points)} AP · ${t.probability}%</span>`)}</div>
      </details>
    </section>`;
  }

  async function refreshStatus() {
    if (!isAuthed()) return;
    try {
      [status, quests] = await Promise.all([get('/gamification/status', { signal }), get('/quests', { signal }).catch(() => quests)]);
    } catch { /* pastram starea existenta */ }
  }

  render();
  if (location.hash) qs(location.hash, root)?.scrollIntoView({ behavior: reduced() ? 'auto' : 'smooth', block: 'start' });

  const offs = [
    on(root, 'click', '[data-claim]', (e, b) => busy(b, async () => {
      try {
        const r = await post('/gamification/daily-claim');
        toastOk(r.message, { icon: '🔥', title: r.milestoneBonus ? `Prag atins: +${num(r.milestoneBonus)} AP bonus!` : null });
        await celebrate(r, { confettiOn: r.milestoneBonus > 0 });
        await refreshStatus();
        render();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-freeze]', async (e, b) => {
      const ok = await confirmDialog({ title: 'Cumperi o protecție de streak?', body: `Costă ${num(status.streakFreezeCost)} AP și se folosește automat dacă ratezi o zi. Poți avea maximum ${status.maxStreakFreezes}.`, confirmText: 'Cumpără' });
      if (!ok) return;
      await busy(b, async () => {
        try {
          const r = await post('/gamification/streak-freeze');
          toastOk(r.message, { icon: '🛡️' });
          await celebrate(r);
          await refreshStatus();
          render();
        } catch (err) { handleError(err); }
      });
    }),
    on(root, 'click', '[data-answer]', (e, b) => {
      const i = Number(b.dataset.answer);
      for (const x of root.querySelectorAll('[data-answer]')) { x.disabled = true; x.setAttribute('aria-pressed', String(x === b)); }
      busy(b, async () => {
        try {
          const r = await post('/gamification/quiz/submit', { questionId: quiz.questionId, answerIndex: i });
          quizResult = { ...r, picked: i };
          if (r.isCorrect) toastOk(r.message, { icon: '🧠' });
          else toast(r.message || 'Răspuns greșit.', { icon: '🤔' });
          await celebrate(r, { confettiOn: r.isCorrect });
          await refreshStatus();
          render();
        } catch (err) {
          handleError(err);
          for (const x of root.querySelectorAll('[data-answer]')) x.disabled = false;
        }
      });
    }),
    on(root, 'click', '[data-fortune]', (e, b) => {
      b.classList.add('is-cracking');
      busy(b, async () => {
        try {
          const [r] = await Promise.all([post('/gamification/fortune-cookie'), new Promise((res) => setTimeout(res, reduced() ? 0 : 700))]);
          fortune = r;
          toastOk(r.message, { icon: '🥠' });
          await celebrate(r);
          await refreshStatus();
          render();
        } catch (err) { handleError(err); b.classList.remove('is-cracking'); }
      });
    }),
    on(root, 'click', '[data-spin]', (e, b) => {
      const segs = config.wheel?.segments || [];
      busy(b, async () => {
        try {
          const r = await post('/gamification/spin-wheel');
          const n = segs.length || 1;
          const seg = 360 / n;
          const jitter = (Math.random() - 0.5) * seg * 0.6;
          const target = (360 - (r.prizeIndex + 0.5) * seg + jitter + 360) % 360;
          const base = wheelRotation - (wheelRotation % 360);
          wheelRotation = base + 360 * (reduced() ? 1 : 6) + target;
          const wheel = qs('#wheel', root);
          wheel.style.setProperty('transform', `rotate(${wheelRotation}deg)`);
          await new Promise((res) => setTimeout(res, reduced() ? 650 : 5100));
          if (!alive()) return;
          if (r.wonPoints > 0) toastOk(r.message, { icon: '🎡', title: `+${num(r.wonPoints)} AP` });
          else toast(r.message, { icon: '🎡' });
          await celebrate(r, { confettiOn: r.wonPoints >= 100 });
          await refreshStatus();
          render();
        } catch (err) { handleError(err); }
      });
    }),
    on(root, 'click', '[data-crate]', async (e, b) => {
      const c = config.crate || {};
      if (!crateResult) {
        const ok = await confirmDialog({ title: 'Deschizi un cufăr?', body: `Costă ${num(c.cost)} AP. Poate conține între ${num(Math.min(...(c.tiers || [{ points: 0 }]).map((t) => t.points)))} și ${num(Math.max(...(c.tiers || [{ points: 0 }]).map((t) => t.points)))} AP.`, confirmText: 'Deschide' });
        if (!ok) return;
      }
      await busy(b, async () => {
        try {
          crateResult = null;
          render();
          const crate = qs('#crate', root);
          crate?.classList.add('is-shaking');
          const [r] = await Promise.all([post('/gamification/open-crate'), new Promise((res) => setTimeout(res, reduced() ? 0 : 900))]);
          if (!alive()) return;
          crateResult = r;
          toastOk(r.message, { icon: '🧰' });
          await celebrate(r, { confettiOn: r.wonPoints > c.cost });
          await refreshStatus();
          render();
        } catch (err) { handleError(err); render(); }
      });
    }),
    on(root, 'click', '[data-quest]', (e, b) => busy(b, async () => {
      try {
        const r = await post(`/quests/${enc(b.dataset.quest)}/claim`);
        toastOk(r.message, { icon: '🎯' });
        await celebrate(r, { confettiOn: true });
        await refreshStatus();
        render();
      } catch (err) { handleError(err); }
    })),
    on(root, 'countdown:end', '[data-countdown]', () => {
      // a trecut miezul noptii: reincarcam starea zilei (o singura data, chiar daca expira mai multe cronometre)
      if (dayRollover) return;
      dayRollover = true;
      setTimeout(async () => {
        dayRollover = false;
        if (!alive()) return;
        await refreshStatus();
        quiz = await get('/gamification/quiz', { signal }).catch(() => quiz);
        quizResult = null;
        fortune = null;
        render();
      }, 1500);
    }),
  ];
  return () => offs.forEach((off) => off());
}
