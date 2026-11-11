import { html, mount, join, qs, on, formData } from '../dom.js';
import { post, get } from '../api.js';
import { icon } from '../icons.js';
import { setMeta } from '../seo.js';
import { navigate, returnPath } from '../router.js';
import { startSession, refreshAll, pendingReferral, clearReferral } from '../store.js';
import { busy, confetti, modal, toast, toastOk, handleError } from '../ui.js';

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const NAME = /^[\p{L}\p{N} ._'-]{2,40}$/u;

export function passwordScore(pw) {
  if (!pw) return 0;
  let s = 0;
  if (pw.length >= 8) s += 1;
  if (pw.length >= 12) s += 1;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) s += 1;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) s += 1;
  if (new Set(pw).size < 4) s = 1;
  return Math.max(1, Math.min(4, s));
}

export function passwordProblem(pw) {
  if (!pw || pw.length < 8) return 'Parola trebuie să aibă cel puțin 8 caractere.';
  if (new TextEncoder().encode(pw).length > 72) return 'Parola poate avea cel mult 72 de caractere.';
  if (new Set(pw).size < 4) return 'Parola este prea simplă. Folosește o combinație mai variată de caractere.';
  return null;
}

export default function auth({ root, query, alive }) {
  const isRegister = location.pathname.replace(/\/$/, '') === '/inregistrare';
  const next = returnPath(query.next);
  const ref = pendingReferral();
  setMeta({
    title: isRegister ? 'Creează cont' : 'Intră în cont',
    description: 'Intră în RewardHub și transformă activitatea din comunitatea Generația Tech în premii reale. Cont nou = 1.000 AP bonus.',
    noindex: !isRegister,
  });
  const nextQs = next !== '/' ? `?next=${encodeURIComponent(next)}` : '';

  mount(root, html`<div class="auth">
    <aside class="auth-aside" aria-label="De ce RewardHub">
      <h2>Comunitatea care te răsplătește pentru că înveți.</h2>
      <ul>
        <li><span class="ico">🎁</span><span><strong>1.000 AP bonus</strong> la înscriere${ref ? ' + 200 AP de la prietenul care te-a invitat' : ''}</span></li>
        <li><span class="ico">🔥</span><span><strong>Streak zilnic</strong>, quiz tehnic, Tech Wordle și misiuni săptămânale</span></li>
        <li><span class="ico">🏆</span><span><strong>Bresle și clasamente</strong> — Java, Python, JavaScript sau DevOps</span></li>
        <li><span class="ico">🛍️</span><span><strong>Premii reale</strong>: tricouri, cursuri, mentorat 1:1, gadgeturi</span></li>
      </ul>
    </aside>
    <section class="card auth-card" aria-labelledby="auth-h">
      <div class="tabs tabs-block" role="tablist">
        <a href="/autentificare${nextQs}" role="tab" ${!isRegister ? html`aria-current="page" aria-selected="true"` : html`aria-selected="false"`}>Intră în cont</a>
        <a href="/inregistrare${nextQs}" role="tab" ${isRegister ? html`aria-current="page" aria-selected="true"` : html`aria-selected="false"`}>Cont nou</a>
      </div>
      <h1 id="auth-h" class="sr-only">${isRegister ? 'Creează cont' : 'Intră în cont'}</h1>
      ${isRegister ? registerForm(ref) : loginForm()}
    </section>
  </div>`);

  const form = qs('form', root);
  const err = qs('.form-error', root);
  const fail = (m, field) => {
    err.textContent = m;
    err.hidden = false;
    if (field) {
      const el = form.elements[field];
      el?.setAttribute('aria-invalid', 'true');
      el?.focus();
    }
  };

  const offs = [
    on(root, 'click', '[data-toggle-pw]', (e, b) => {
      const input = qs('#f-password', root);
      const show = input.type === 'password';
      input.type = show ? 'text' : 'password';
      b.setAttribute('aria-pressed', String(show));
      b.setAttribute('aria-label', show ? 'Ascunde parola' : 'Arată parola');
      mount(b, icon(show ? 'eyeOff' : 'eye'));
    }),
    on(root, 'input', '#f-password', (e, input) => {
      const meter = qs('.strength', root);
      if (meter) meter.dataset.level = String(input.value ? passwordScore(input.value) : 0);
    }),
    on(root, 'input', 'input', (e, input) => {
      input.removeAttribute('aria-invalid');
      err.hidden = true;
    }),
    on(root, 'submit', 'form', async (e) => {
      e.preventDefault();
      err.hidden = true;
      const d = formData(form);
      d.password = form.elements.password.value; // parola nu se taie la capete
      if (!EMAIL.test(d.email || '')) return fail('Introdu o adresă de email validă.', 'email');
      if (isRegister) {
        if (!NAME.test(d.name || '')) return fail('Numele trebuie să aibă 2-40 de caractere (litere, cifre, spațiu, . _ \' -).', 'name');
        const p = passwordProblem(d.password);
        if (p) return fail(p, 'password');
        if (!form.elements.terms.checked) return fail('Trebuie să accepți termenii și politica de confidențialitate.', 'terms');
      } else if (!d.password) {
        return fail('Introdu parola.', 'password');
      }
      await busy(qs('[type=submit]', form), async () => {
        try {
          const res = isRegister
            ? await post('/auth/register', { name: d.name, email: d.email, password: d.password, referralCode: d.referralCode || null }, { auth: false })
            : await post('/auth/login', { email: d.email, password: d.password }, { auth: false });
          if (!alive()) return;
          startSession(res);
          refreshAll();
          if (isRegister) {
            clearReferral();
            confetti();
            toastOk(res.message || 'Cont creat!', { title: `Bun venit, ${res.user.name}!`, icon: '🎉', timeout: 6000 });
            navigate(next, { replace: true });
            onboarding();
          } else {
            toast(`Bine ai revenit, ${res.user.name.split(' ')[0]}! 👋`, { type: 'success' });
            navigate(next, { replace: true });
          }
        } catch (e2) {
          if (e2.code === 'INVALID_REFERRAL') return fail(e2.message, 'referralCode');
          if (e2.status === 429) return fail(e2.message);
          fail(e2.message || 'Nu am putut finaliza cererea.');
        }
      });
    }),
  ];
  qs(isRegister ? '#f-name' : '#f-email', root)?.focus();
  return () => offs.forEach((off) => off());
}

function passwordField(autocomplete) {
  return html`<div class="field">
    <label for="f-password">Parolă</label>
    <div class="input-wrap">
      <input class="input" id="f-password" name="password" type="password" autocomplete="${autocomplete}" maxlength="128" required ${autocomplete === 'new-password' ? html`minlength="8" aria-describedby="pw-hint"` : ''}>
      <button class="btn btn-ghost btn-sm btn-icon input-action" type="button" data-toggle-pw aria-pressed="false" aria-label="Arată parola">${icon('eye')}</button>
    </div>
    ${autocomplete === 'new-password' ? html`<div class="strength" data-level="0" aria-hidden="true"><span></span><span></span><span></span><span></span></div>
      <span class="hint" id="pw-hint">Minimum 8 caractere. Un passphrase lung e cel mai sigur.</span>` : ''}
  </div>`;
}

function loginForm() {
  return html`<form class="stack" novalidate>
    <div class="field"><label for="f-email">Email</label><input class="input" id="f-email" name="email" type="email" autocomplete="email" inputmode="email" maxlength="254" required></div>
    ${passwordField('current-password')}
    <div class="form-error" role="alert" hidden></div>
    <button class="btn btn-primary btn-lg btn-block" type="submit">Intră în cont</button>
    <p class="small muted center">Nu ai cont? <a href="/inregistrare">Creează unul în 20 de secunde</a> și primești 1.000 AP.</p>
  </form>`;
}

function registerForm(ref) {
  return html`<form class="stack" novalidate>
    <div class="field"><label for="f-name">Nume afișat</label><input class="input" id="f-name" name="name" autocomplete="nickname" maxlength="40" required placeholder="Ex: Ana Pop"></div>
    <div class="field"><label for="f-email">Email</label><input class="input" id="f-email" name="email" type="email" autocomplete="email" inputmode="email" maxlength="254" required></div>
    ${passwordField('new-password')}
    <div class="field">
      <label for="f-ref">Cod de invitație <span class="muted">(opțional)</span></label>
      <input class="input" id="f-ref" name="referralCode" maxlength="32" autocomplete="off" autocapitalize="characters" placeholder="GT-XXXXXX" value="${ref}">
      ${ref ? html`<span class="hint">🎁 Ai fost invitat! Primești 200 AP în plus.</span>` : ''}
    </div>
    <label class="check"><input type="checkbox" name="terms" required> <span>Am citit și accept <a href="/termeni" target="_blank" rel="noopener">termenii</a> și <a href="/confidentialitate" target="_blank" rel="noopener">politica de confidențialitate</a>.</span></label>
    <div class="form-error" role="alert" hidden></div>
    <button class="btn btn-primary btn-lg btn-block" type="submit">Creează contul · +1.000 AP</button>
    <p class="small muted center">Ai deja cont? <a href="/autentificare">Intră aici</a>.</p>
  </form>`;
}

/** Dupa inregistrare: alegerea breslei (optional). */
async function onboarding() {
  let guilds;
  try {
    guilds = (await get('/guilds'))?.guilds || [];
  } catch { return; }
  if (!guilds.length) return;
  await modal({
    title: 'Alege-ți breasla',
    content: html`<p class="muted">Breasla ta concurează săptămânal cu celelalte. Tot XP-ul pe care îl câștigi contează pentru ea. O poți schimba mai târziu.</p>
      <div class="stack stack-sm">${join(guilds, (g) => html`<button class="card card-pad card-link row row-nowrap" type="button" data-guild="${g.id}">
        <span class="big-emoji" aria-hidden="true">${g.icon}</span>
        <span class="grow"><strong>${g.name}</strong><br><span class="small muted">${g.description}</span></span>
        ${icon('chevronRight')}
      </button>`)}</div>`,
    footer: html`<button class="btn btn-ghost" type="button" data-close>Mai târziu</button>`,
    onMount: (dlg, close) => on(dlg, 'click', '[data-guild]', async (e, b) => {
      await busy(b, async () => {
        try {
          const r = await post('/guilds/select', { guildId: b.dataset.guild });
          toastOk(r.message, { icon: r.guild?.icon || '🛡️' });
          close(true);
        } catch (err) {
          if (err.code === 'ALREADY_MEMBER') {
            toastOk(err.message, { icon: '🛡️' });
            close(true);
          } else handleError(err);
        }
      });
    }),
  });
}
