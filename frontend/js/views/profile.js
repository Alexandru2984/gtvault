import { html, mount, join, qs, on, formData, productImage, avatarSrc } from '../dom.js';
import { get, post, enc, query as qstr, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { date, num, plural, percent } from '../format.js';
import { apAmount, avatar, emptyState, progress, skeletonPage, stars } from '../components.js';
import { setMeta } from '../seo.js';
import { navigate, setQuery } from '../router.js';
import { state, setUser, startSession, endSession, refreshUser } from '../store.js';
import { busy, celebrate, confirmDialog, copyText, downloadBlob, handleError, modal, shareLink, toastOk } from '../ui.js';
import { toggleTheme, currentTheme } from '../theme-toggle.js';
import { passwordProblem } from './auth.js';
import { sessionSettings } from '../session-settings.js';

const TABS = {
  general: 'General',
  insigne: 'Insigne',
  recenzii: 'Recenzii',
  invita: 'Invită prieteni',
  cadou: 'Trimite cadou',
  setari: 'Setări',
};
const RARITY = { COMMON: 'Comune', UNCOMMON: 'Neobișnuite', RARE: 'Rare', EPIC: 'Epice', LEGENDARY: 'Legendare' };

export default async function profile({ root, query, signal, alive }) {
  setMeta({ title: 'Profilul meu', noindex: true });
  let tab = TABS[query.tab] ? query.tab : 'general';
  mount(root, skeletonPage());
  let stats;
  try {
    stats = await get('/user/stats', { signal });
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  const offs = [];
  let tabCleanup = null;
  let tabGeneration = 0;

  function shell() {
    const u = state.user || {};
    const t = stats.tier || {};
    mount(root, html`<div class="page">
      <section class="card card-pad stack">
        <div class="profile-head">
          <button class="avatar-edit" type="button" data-avatar aria-label="Schimbă avatarul">${avatar(u.avatar, u.name, 'xl')}<span class="pen">${icon('edit')}</span></button>
          <div class="stack stack-sm">
            <h1>${u.name}</h1>
            <div class="row"><span class="tag tag-brand">${t.icon} ${t.title}</span>${!u.publicProfile ? html`<span class="tag">${icon('lock')} Profil privat</span>` : html`<a class="small" href="/membru/${enc(u.id)}">Vezi profilul public ${icon('external')}</a>`}</div>
            ${u.bio ? html`<p class="small muted">${u.bio}</p>` : ''}
          </div>
        </div>
        <div class="kpis">
          <div class="kpi"><span class="kpi-label">Sold</span><span class="kpi-value">${apAmount(stats.balance)}</span><a class="kpi-sub" href="/istoric">Istoric →</a></div>
          <div class="kpi"><span class="kpi-label">XP total</span><span class="kpi-value">${num(stats.xp)}</span><span class="kpi-sub">+${num(stats.xpThisWeek)} săptămâna asta</span></div>
          <div class="kpi"><span class="kpi-label">Streak</span><span class="kpi-value">🔥 ${num(u.streakDays)}</span><span class="kpi-sub">record ${plural(u.longestStreak || 0, 'zi', 'zile')}</span></div>
          <div class="kpi"><span class="kpi-label">Reducere nivel</span><span class="kpi-value">${t.discountPercent || 0}%</span><span class="kpi-sub">la fiecare comandă</span></div>
        </div>
      </section>
      <nav class="tabs" aria-label="Secțiuni profil">${join(Object.entries(TABS), ([k, v]) => html`<a href="/profil${k === 'general' ? '' : `?tab=${k}`}" data-tab="${k}" ${k === tab ? html`aria-current="page"` : ''}>${v}</a>`)}</nav>
      <div id="tab-body"><div class="skel skel-block"></div></div>
    </div>`);
  }

  async function showTab() {
    const generation = ++tabGeneration;
    if (typeof tabCleanup === 'function') tabCleanup();
    tabCleanup = null;
    const body = qs('#tab-body', root);
    mount(body, html`<div class="skel skel-block"></div>`);
    try {
      const tabAlive = () => alive() && generation === tabGeneration;
      const cleanup = await TAB_VIEWS[tab]({ body, stats, signal, alive: tabAlive, rerender: () => { shell(); showTab(); } });
      if (tabAlive()) tabCleanup = cleanup;
      else if (typeof cleanup === 'function') cleanup();
    } catch (err) {
      if (alive() && generation === tabGeneration && !isAbort(err)) mount(body, emptyState({ icon: '🔌', title: 'Nu am putut încărca secțiunea', text: err.message }));
    }
  }

  shell();
  await showTab();

  offs.push(on(root, 'click', '[data-tab]', (e, a) => {
    e.preventDefault();
    e.stopPropagation();
    tab = a.dataset.tab;
    setQuery({ tab: tab === 'general' ? '' : tab });
    for (const x of root.querySelectorAll('[data-tab]')) {
      if (x === a) x.setAttribute('aria-current', 'page');
      else x.removeAttribute('aria-current');
    }
    showTab();
  }, true));
  offs.push(on(root, 'click', '[data-avatar]', () => avatarPicker(() => { shell(); showTab(); })));

  return () => {
    tabGeneration++;
    if (typeof tabCleanup === 'function') tabCleanup();
    offs.forEach((off) => off());
  };
}

// ------------------------------------------------------------------ tab-uri

const TAB_VIEWS = { general, insigne, recenzii, invita, cadou, setari };

async function general({ body, stats, signal, alive }) {
  const [badges, guild] = await Promise.all([
    get('/gamification/badges', { signal }).catch(() => null),
    get('/guilds/me', { signal }).catch(() => null),
  ]);
  if (!alive()) return;
  const t = stats.tier || {};
  const recent = (badges?.badges || []).filter((b) => b.unlocked).sort((a, b) => Date.parse(b.unlockedAt) - Date.parse(a.unlockedAt)).slice(0, 6);
  const near = (badges?.badges || []).filter((b) => !b.unlocked && b.target > 1 && b.progress > 0).sort((a, b) => b.progress / b.target - a.progress / a.target).slice(0, 3);
  mount(body, html`<div class="grid-2">
    <section class="card card-pad tier-card" aria-labelledby="tier-h">
      <div class="row"><span class="tier-icon" aria-hidden="true">${t.icon}</span><div><h2 id="tier-h" class="card-title">Nivel ${t.level}: ${t.title}</h2><span class="small muted">${num(t.currentXp)} XP</span></div></div>
      ${t.nextTier ? html`${progress(t.progressPercent || 0, '', 'Progres nivel')}<span class="small muted">Încă ${num(t.xpToNext)} XP până la ${t.nextTier}</span>` : html`<span class="tag tag-gold">Nivel maxim atins</span>`}
      <ul class="perks-list">${join(t.perks || [], (p) => html`<li>${icon('check')} ${p}</li>`)}</ul>
    </section>
    <section class="card card-pad stack" aria-labelledby="g-h">
      <h2 class="card-title" id="g-h">🛡️ Breasla ta</h2>
      ${guild ? html`<div class="row row-nowrap"><span class="big-emoji">${guild.icon}</span><div><strong>${guild.name}</strong><p class="small muted">${guild.description}</p></div></div>
        <a class="btn btn-secondary btn-sm" href="/bresle">Vezi clasamentul breslelor</a>` : html`<a class="btn btn-secondary" href="/bresle">Alege o breaslă</a>`}
    </section>
    <section class="card card-pad stack" aria-labelledby="rb-h">
      <div class="row-between"><h2 class="card-title" id="rb-h">🏅 Insigne recente</h2><a class="small strong" href="/profil?tab=insigne" data-tab="insigne">${badges ? `${badges.unlockedCount}/${badges.totalCount}` : ''} →</a></div>
      ${recent.length ? html`<div class="badges-grid">${join(recent, badgeTile)}</div>` : html`<p class="muted">Prima insignă e aproape: revendică bonusul zilnic sau rezolvă quiz-ul.</p>`}
    </section>
    <section class="card card-pad stack" aria-labelledby="nb-h">
      <h2 class="card-title" id="nb-h">🎯 Aproape de deblocare</h2>
      ${near.length ? join(near, (b) => html`<div class="stack stack-sm"><div class="row-between"><span><span aria-hidden="true">${b.icon}</span> <strong>${b.title}</strong></span><span class="small muted">${b.progress}/${b.target}</span></div>${progress(percent(b.progress, b.target), 'progress-sm', b.title)}<span class="xsmall muted">${b.description}</span></div>`)
        : html`<p class="muted">Continuă activitățile zilnice și vei vedea aici insignele în progres.</p>`}
    </section>
  </div>`);
}

function badgeTile(b) {
  return html`<div class="badge-tile ${b.unlocked ? '' : 'is-locked'}" data-rarity="${b.rarity}" title="${b.description}">
    <span class="badge-ico" aria-hidden="true">${b.icon}</span>
    <span class="badge-name">${b.title}</span>
    <span class="rarity">${RARITY[b.rarity] || b.rarity}</span>
    <span class="badge-desc">${b.description}</span>
    ${b.unlocked ? html`<span class="xsmall muted">${date(b.unlockedAt)}${b.rewardPoints ? ` · +${b.rewardPoints} AP` : ''}</span>`
      : b.target > 1 ? html`${progress(percent(b.progress, b.target), 'progress-sm', b.title)}<span class="xsmall muted">${b.progress}/${b.target}</span>`
      : html`<span class="xsmall muted">${b.rewardPoints ? `+${b.rewardPoints} AP` : 'Blocată'}</span>`}
  </div>`;
}

async function insigne({ body, signal, alive }) {
  const d = await get('/gamification/badges', { signal });
  if (!alive()) return undefined;
  let filter = 'all';
  const render = () => {
    const list = (d.badges || []).filter((b) => filter === 'all' || (filter === 'unlocked' ? b.unlocked : filter === 'locked' ? !b.unlocked : b.rarity === filter));
    mount(body, html`<div class="stack">
      <div class="row-between"><p class="muted">${d.unlockedCount} din ${d.totalCount} insigne deblocate</p>
        <div class="chips" role="group" aria-label="Filtru insigne">
          ${join([['all', 'Toate'], ['unlocked', 'Deblocate'], ['locked', 'Blocate'], ...Object.entries(RARITY).filter(([k]) => (d.badges || []).some((b) => b.rarity === k))], ([k, v]) => html`<button class="chip" type="button" data-bf="${k}" aria-pressed="${k === filter}">${v}</button>`)}
        </div></div>
      ${progress(percent(d.unlockedCount, d.totalCount), 'is-gold', 'Colecția de insigne')}
      <div class="badges-grid">${join(list, badgeTile)}</div>
    </div>`);
  };
  render();
  return on(body, 'click', '[data-bf]', (e, b) => { filter = b.dataset.bf; render(); });
}

async function recenzii({ body, signal, alive }) {
  const [mine, pending] = await Promise.all([get('/user/reviews', { signal }), get('/user/reviews/pending', { signal })]);
  if (!alive()) return;
  mount(body, html`<div class="stack stack-lg">
    ${pending.length ? html`<section class="card card-pad stack" aria-labelledby="pend-h">
      <h2 class="card-title" id="pend-h">⭐ Așteaptă recenzia ta</h2>
      <p class="small muted">Ai primit aceste premii. O recenzie ajută comunitatea și îți aduce AP.</p>
      ${join(pending, (p) => html`<a class="todo" href="/produs/${enc(p.rewardId)}#recenzii">
        <img class="avatar" src="${productImage(p)}" alt="" width="40" height="40">
        <span class="grow"><span class="todo-title">${p.name}</span><br><span class="todo-meta">primit ${date(p.boughtAt)}</span></span>
        ${p.rewardPoints ? html`<span class="tag tag-gold">+${p.rewardPoints} AP</span>` : ''}${icon('chevronRight')}
      </a>`)}
    </section>` : ''}
    <section class="card card-pad stack" aria-labelledby="mine-h">
      <h2 class="card-title" id="mine-h">${icon('message')} Recenziile mele</h2>
      ${mine.length ? join(mine, (r) => html`<article class="review">
        <div class="row-between"><a class="strong" href="/produs/${enc(r.rewardId)}#recenzii">${r.rewardName}</a><span class="small muted">${date(r.createdAt)}</span></div>
        <div class="row">${stars(r.rating)}${r.hidden ? html`<span class="tag tag-danger">Ascunsă de moderator</span>` : ''}${r.helpfulCount ? html`<span class="small muted">${plural(r.helpfulCount, 'vot util', 'voturi utile')}</span>` : ''}</div>
        <p class="review-body">${r.comment}</p></article>`)
        : html`<p class="muted">Nu ai scris încă nicio recenzie.</p>`}
    </section>
  </div>`);
}

async function invita({ body, signal, alive }) {
  const r = await get('/user/referrals', { signal });
  if (!alive()) return undefined;
  mount(body, html`<div class="grid-2">
    <section class="card card-pad stack" aria-labelledby="inv-h">
      <h2 class="card-title" id="inv-h">${icon('gift')} Invită prieteni, câștigați amândoi</h2>
      <p class="muted">Pentru fiecare prieten care se înscrie cu linkul tău primești <strong>${num(r.bonusPerFriend)} AP</strong>, iar el primește <strong>${num(r.friendBonus)} AP</strong> în plus față de bonusul de bun venit.</p>
      <div class="referral-box"><code>${r.referralLink}</code><button class="btn btn-secondary btn-sm" type="button" data-copy="${r.referralLink}">${icon('copy')} Copiază</button></div>
      <div class="row">
        <button class="btn btn-primary" type="button" data-share-ref>${icon('share')} Trimite invitația</button>
        <span class="small muted">Codul tău: <strong class="mono">${r.referralCode}</strong></span>
      </div>
    </section>
    <section class="card card-pad stack" aria-labelledby="fr-h">
      <h2 class="card-title" id="fr-h">${icon('users')} Prietenii tăi</h2>
      <div class="kpis">
        <div class="kpi"><span class="kpi-label">Invitați</span><span class="kpi-value">${num(r.invitedCount)}</span></div>
        <div class="kpi"><span class="kpi-label">AP câștigat</span><span class="kpi-value">${num(r.earnedPoints)}</span><span class="kpi-sub">max ${r.maxRewarded} prieteni recompensați</span></div>
      </div>
      ${r.friends?.length ? html`<ul class="feed">${join(r.friends, (f) => html`<li class="feed-item">${avatar(f.avatar, f.name)}<div><div class="feed-title">${f.name}</div><div class="feed-at">s-a alăturat ${date(f.joinedAt)}</div></div></li>`)}</ul>`
        : html`<p class="muted">Niciun prieten încă. Trimite linkul pe grupul de facultate sau pe Discord!</p>`}
    </section>
  </div>`);
  return on(body, 'click', '[data-copy], [data-share-ref]', (e, b) => {
    if (b.dataset.copy) copyText(b.dataset.copy, 'Link de invitație copiat!');
    else shareLink({ title: 'Hai în RewardHub', text: `Intră în comunitatea Generația Tech cu linkul meu și primești ${num(r.friendBonus)} AP în plus!`, url: r.referralLink });
  });
}

async function cadou({ body, signal, alive }) {
  const limits = await get('/user/gift/limits', { signal });
  if (!alive()) return undefined;
  let recipient = null;
  const render = () => {
    mount(body, html`<section class="card card-pad stack" aria-labelledby="gift-h">
      <h2 class="card-title" id="gift-h">${icon('gift')} Trimite AP unui prieten</h2>
      ${!limits.eligible ? html`<div class="callout is-warning">${icon('info')}<span>${limits.reason}</span></div>` : ''}
      <p class="small muted">Între ${num(limits.min)} și ${num(limits.max)} AP per cadou · azi ai trimis ${num(limits.sentToday)}/${num(limits.dailyAmount)} AP în ${limits.giftsToday}/${limits.dailyCount} cadouri.</p>
      <form class="stack" id="gift-form" novalidate>
        <div class="field">
          <label for="g-code">Codul GT al prietenului</label>
          <div class="input-group"><input class="input" id="g-code" name="recipient" placeholder="GT-XXXXXX" maxlength="20" autocomplete="off" autocapitalize="characters" value="${recipient?.code || ''}" ${!limits.eligible ? 'disabled' : ''}>
          <button class="btn btn-secondary" type="button" data-lookup ${!limits.eligible ? 'disabled' : ''}>Caută</button></div>
          <span class="hint">Îl găsește în profil → Invită prieteni.</span>
        </div>
        ${recipient ? html`<div class="todo">${avatar(recipient.avatar, recipient.name)}<span class="grow"><span class="todo-title">${recipient.name}</span><br><span class="todo-meta">Destinatar confirmat</span></span>${icon('check')}</div>
          <div class="form-grid">
            <div class="field"><label for="g-amount">Sumă (AP)</label><input class="input" id="g-amount" name="amount" type="number" inputmode="numeric" min="${limits.min}" max="${limits.max}" step="1" required value="${limits.min}"></div>
            <div class="field"><label for="g-msg">Mesaj <span class="muted">(opțional)</span></label><input class="input" id="g-msg" name="message" maxlength="140" placeholder="Spor la învățat! 🚀"></div>
          </div>
          <button class="btn btn-gold" type="submit">${icon('send')} Trimite cadoul</button>` : ''}
        <div class="form-error" role="alert" hidden></div>
      </form>
    </section>`);
  };
  render();
  const fail = (m) => { const e = qs('.form-error', body); e.textContent = m; e.hidden = false; };
  const offs = [
    on(body, 'click', '[data-lookup]', (e, b) => busy(b, async () => {
      const code = qs('#g-code', body).value.trim().toUpperCase();
      if (!/^GT-[A-Z0-9]{4,12}$/.test(code)) return fail('Codul are forma GT-XXXXXX.');
      try {
        const r = await get(`/user/gift/recipient${qstr({ code })}`);
        recipient = { ...r, code };
        render();
        qs('#g-amount', body)?.focus();
      } catch (err) { fail(err.message); }
      return undefined;
    })),
    on(body, 'submit', '#gift-form', async (e, form) => {
      e.preventDefault();
      if (!recipient) return;
      const d = formData(form);
      const amount = Number(d.amount);
      if (!Number.isInteger(amount) || amount < limits.min || amount > limits.max) return fail(`Suma trebuie să fie între ${limits.min} și ${limits.max} AP.`);
      const ok = await confirmDialog({ title: 'Confirmi cadoul?', body: `Trimiți ${num(amount)} AP către ${recipient.name}. Transferul nu poate fi anulat.`, confirmText: `Trimite ${num(amount)} AP` });
      if (!ok) return;
      await busy(qs('[type=submit]', form), async () => {
        try {
          const r = await post('/user/gift', { recipient: recipient.code, amount, message: d.message || null });
          toastOk(r.message, { icon: '🎁' });
          await celebrate(r, { confettiOn: true });
          recipient = null;
          Object.assign(limits, await get('/user/gift/limits').catch(() => limits));
          render();
        } catch (err) { fail(err.message); }
      });
    }),
  ];
  return () => offs.forEach((off) => off());
}

async function setari({ body, signal, alive, rerender }) {
  const u = state.user || {};
  if (!alive()) return undefined;
  mount(body, html`<div class="grid-2 account-settings">
    <section class="card card-pad stack" aria-labelledby="pf-h">
      <h2 class="card-title" id="pf-h">${icon('user')} Profil</h2>
      <form class="stack" id="profile-form" novalidate>
        <div class="field"><label for="s-name">Nume afișat</label><input class="input" id="s-name" name="name" maxlength="40" required value="${u.name}"></div>
        <div class="field"><label for="s-bio">Despre tine <span class="muted">(opțional)</span></label><textarea class="textarea" id="s-bio" name="bio" maxlength="160" placeholder="Ex: Student la Automatică, pasionat de backend.">${u.bio || ''}</textarea><span class="hint">Max 160 caractere, apare pe profilul public.</span></div>
        <label class="switch"><input type="checkbox" name="publicProfile" ${u.publicProfile ? 'checked' : ''}> Profil public (apari cu nume și insigne în clasament și comunitate)</label>
        <div class="form-error" role="alert" hidden></div>
        <button class="btn btn-primary" type="submit">Salvează</button>
      </form>
    </section>
    <section class="card card-pad stack" aria-labelledby="pw-h">
      <h2 class="card-title" id="pw-h">${icon('lock')} Parolă și sesiuni</h2>
      <form class="stack" id="pw-form" novalidate>
        <input type="text" name="username" autocomplete="username" value="${u.email}" hidden>
        <div class="field"><label for="s-cur">Parola curentă</label><input class="input" id="s-cur" name="currentPassword" type="password" autocomplete="current-password" maxlength="128" required></div>
        <div class="field"><label for="s-new">Parola nouă</label><input class="input" id="s-new" name="newPassword" type="password" autocomplete="new-password" minlength="8" maxlength="128" required></div>
        <div class="form-error" role="alert" hidden></div>
        <button class="btn btn-secondary" type="submit">Schimbă parola</button>
      </form>
      <hr>
      <p class="small muted">Ai uitat contul deschis pe alt dispozitiv? Deconectează toate celelalte sesiuni.</p>
      <button class="btn btn-ghost" type="button" data-logout-all>${icon('logout')} Deconectează celelalte dispozitive</button>
    </section>
    <section class="card card-pad stack" data-session-panel aria-label="Sesiuni active"></section>
    <section class="card card-pad stack" aria-labelledby="ap-h">
      <h2 class="card-title" id="ap-h">${icon('sun')} Aspect</h2>
      <label class="switch"><input type="checkbox" data-theme-switch ${currentTheme() === 'dark' ? 'checked' : ''}> Temă întunecată</label>
    </section>
    <section class="card card-pad stack" aria-labelledby="gd-h">
      <h2 class="card-title" id="gd-h">${icon('shield')} Datele tale</h2>
      <p class="small muted">Descarcă tot ce știm despre tine (profil, tranzacții, comenzi, recenzii) într-un fișier JSON. <a href="/confidentialitate">Politica de confidențialitate</a></p>
      <button class="btn btn-secondary" type="button" data-export>${icon('download')} Descarcă datele mele</button>
    </section>
    <section class="card card-pad stack danger-zone span-2" aria-labelledby="del-h">
      <h2 class="card-title" id="del-h">${icon('alert')} Șterge contul</h2>
      <p class="small muted">Contul, soldul de AP, insignele, recenziile și istoricul se șterg definitiv. Nu se poate anula. Dacă ai comenzi în curs, anulează-le sau așteaptă livrarea mai întâi.</p>
      <button class="btn btn-danger-soft" type="button" data-delete-account>${icon('trash')} Șterge definitiv contul</button>
    </section>
  </div>`);

  const sessions = await sessionSettings(qs('[data-session-panel]', body), { signal, alive });
  const offs = [
    on(body, 'submit', '#profile-form', async (e, form) => {
      e.preventDefault();
      const d = formData(form);
      const err = qs('.form-error', form);
      err.hidden = true;
      if (!/^[\p{L}\p{N} ._'-]{2,40}$/u.test(d.name || '')) {
        err.textContent = 'Numele trebuie să aibă 2-40 de caractere (litere, cifre, spațiu, . _ \' -).';
        err.hidden = false;
        return;
      }
      await busy(qs('[type=submit]', form), async () => {
        try {
          const r = await post('/user/me', { name: d.name, bio: d.bio || '', publicProfile: form.elements.publicProfile.checked });
          setUser(r.user);
          toastOk(r.message || 'Profil actualizat.');
          rerender();
        } catch (e2) { err.textContent = e2.message; err.hidden = false; }
      });
    }),
    on(body, 'submit', '#pw-form', async (e, form) => {
      e.preventDefault();
      const err = qs('.form-error', form);
      err.hidden = true;
      const cur = form.elements.currentPassword.value;
      const next = form.elements.newPassword.value;
      const problem = !cur ? 'Introdu parola curentă.' : passwordProblem(next) || (cur === next ? 'Parola nouă trebuie să fie diferită.' : null);
      if (problem) { err.textContent = problem; err.hidden = false; return; }
      await busy(qs('[type=submit]', form), async () => {
        try {
          const r = await post('/auth/password', { currentPassword: cur, newPassword: next });
          startSession(r);
          form.reset();
          toastOk(r.message, { icon: '🔐' });
          await sessions.refresh();
        } catch (e2) { err.textContent = e2.message; err.hidden = false; }
      });
    }),
    on(body, 'click', '[data-logout-all]', async (e, b) => {
      if (!(await confirmDialog({ title: 'Deconectezi celelalte dispozitive?', body: 'Rămâi conectat doar aici. Pe celelalte dispozitive va trebui să intri din nou.', confirmText: 'Deconectează' }))) return;
      await busy(b, async () => {
        try {
          const r = await post('/auth/logout-all');
          startSession(r);
          toastOk(r.message, { icon: '🔐' });
          await sessions.refresh();
        } catch (e2) { handleError(e2); }
      });
    }),
    on(body, 'change', '[data-theme-switch]', () => toggleTheme()),
    on(body, 'click', '[data-export]', (e, b) => busy(b, async () => {
      try {
        const data = await get('/user/export');
        const day = new Date().toISOString().slice(0, 10);
        downloadBlob(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }), `rewardhub-datele-mele-${day}.json`);
        toastOk('Fișierul a fost descărcat.', { icon: '📦' });
      } catch (e2) { handleError(e2); }
    })),
    on(body, 'click', '[data-delete-account]', () => deleteAccount()),
  ];
  return () => { sessions.cleanup(); offs.forEach((off) => off()); };
}

function deleteAccount() {
  return modal({
    title: 'Ștergi definitiv contul?',
    content: html`<div class="callout is-danger">${icon('alert')}<span>Pierzi soldul de ${num(state.user?.activityPoints || 0)} AP, insignele, streak-ul și istoricul. Acțiunea este ireversibilă.</span></div>
      <form class="stack" id="del-form" novalidate>
        <input type="text" name="username" autocomplete="username" value="${state.user?.email || ''}" hidden>
        <div class="field"><label for="d-pw">Parola</label><input class="input" id="d-pw" name="password" type="password" autocomplete="current-password" required></div>
        <div class="field"><label for="d-conf">Scrie <strong>STERGE</strong> pentru confirmare</label><input class="input" id="d-conf" name="confirmation" autocomplete="off" autocapitalize="characters" required></div>
        <div class="form-error" role="alert" hidden></div>
      </form>`,
    footer: html`<button class="btn btn-ghost" type="button" data-close>Renunță</button><button class="btn btn-danger" type="submit" form="del-form">Șterge contul</button>`,
    onMount: (dlg, close) => {
      const form = qs('#del-form', dlg);
      form.addEventListener('submit', async (e) => {
        e.preventDefault();
        const err = qs('.form-error', dlg);
        const password = form.elements.password.value;
        const confirmation = form.elements.confirmation.value.trim();
        if (confirmation !== 'STERGE') { err.textContent = 'Scrie exact STERGE (cu majuscule).'; err.hidden = false; return; }
        await busy(qs('[type=submit]', dlg), async () => {
          try {
            await post('/user/account/delete', { password, confirmation });
            close(true);
            endSession();
            toastOk('Contul a fost șters. Ne pare rău că pleci! 👋', { timeout: 7000 });
            navigate('/', { replace: true });
          } catch (e2) { err.textContent = e2.message; err.hidden = false; }
        });
      });
    },
  });
}

// ------------------------------------------------------------------ avatar

async function avatarPicker(onChange) {
  let list;
  try {
    list = await get('/user/avatars');
  } catch (err) { handleError(err); return; }
  const all = [list.default, ...(list.avatars || [])];
  const current = state.user?.avatar;
  await modal({
    title: 'Alege-ți avatarul',
    size: 'lg',
    content: html`<div class="avatar-picker" role="group" aria-label="Avatare">${join(all, (a) => html`<button type="button" data-pick="${a}" aria-pressed="${a === current}" aria-label="Avatar ${a.split('/').pop().replace('.svg', '')}"><img src="${avatarSrc(a)}" alt="" width="76" height="76" loading="lazy"></button>`)}</div>`,
    onMount: (dlg, close) => on(dlg, 'click', '[data-pick]', async (e, b) => {
      try {
        const r = await post('/user/me', { avatar: b.dataset.pick });
        setUser(r.user);
        toastOk('Avatar actualizat!', { icon: '🖼️' });
        close(true);
        refreshUser();
        onChange();
      } catch (err) { handleError(err); }
    }),
  });
}
