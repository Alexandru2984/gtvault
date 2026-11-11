import { html, mount, join, qs, qsa, on, vars } from '../dom.js';
import { get, post, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { num, percent } from '../format.js';
import { countdownEl, loginPrompt, skeletonPage, apAmount } from '../components.js';
import { setMeta } from '../seo.js';
import { isAuthed } from '../store.js';
import { celebrate, handleError, modal, shareLink, toast, copyText } from '../ui.js';

const ROWS = ['QWERTYUIOP', 'ASDFGHJKL', 'ZXCVBNM'];
const REVEAL_MS = 250;
const reduced = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches;

export default async function wordle({ root, signal, alive }) {
  setMeta({
    title: 'Tech Wordle',
    description: 'Ghicește cuvântul tech al zilei din 6 încercări: limbaje, unelte, concepte de programare. Câștigi AP în funcție de cât de repede îl găsești.',
  });
  mount(root, skeletonPage());
  let g;
  try {
    g = await get('/games/wordle', { signal });
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  let buffer = '';
  let sending = false;
  let revealRow = -1;
  let keyboard = { ...(g.keyboard || {}) };
  const L = g.length || 5;
  const MAX = g.maxGuesses || 6;

  function tiles(rowIndex) {
    const guess = g.guesses[rowIndex];
    if (guess) {
      return join([...guess.word], (ch, i) => html`<div class="wd-tile" data-state="${guess.marks[i]}" aria-label="${ch}: ${label(guess.marks[i])}">${ch}</div>`);
    }
    const active = rowIndex === g.guesses.length && g.status === 'PLAYING';
    return join(Array.from({ length: L }), (_, i) => {
      const ch = active ? buffer[i] || '' : '';
      return html`<div class="wd-tile" data-state="${ch ? 'filled' : ''}">${ch}</div>`;
    });
  }

  function board() {
    return join(Array.from({ length: MAX }), (_, r) => html`<div class="wd-row ${r === revealRow ? 'is-revealing' : ''}" role="row" aria-label="Încercarea ${r + 1}">${tiles(r)}</div>`);
  }

  function kbd() {
    const key = (k) => html`<button class="wd-key" type="button" data-key="${k}" ${keyboard[k] ? html`data-state="${keyboard[k]}"` : ''} aria-label="${k}${keyboard[k] ? `, ${label(keyboard[k])}` : ''}">${k}</button>`;
    return html`<div class="wd-kbd" aria-label="Tastatură">
      <div class="wd-kbd-row">${join([...ROWS[0]], key)}</div>
      <div class="wd-kbd-row">${join([...ROWS[1]], key)}</div>
      <div class="wd-kbd-row">
        <button class="wd-key is-wide" type="button" data-key="ENTER">TRIMITE</button>
        ${join([...ROWS[2]], key)}
        <button class="wd-key is-wide" type="button" data-key="BACK" aria-label="Șterge litera">${icon('backspace')}</button>
      </div>
    </div>`;
  }

  function render() {
    const done = g.status !== 'PLAYING';
    mount(root, html`<div class="page">
      <div class="page-head">
        <div><h1>Tech Wordle <span class="muted">#${g.puzzleNumber}</span></h1>
          <p>Ghicește cuvântul tech de ${L} litere. ${g.community ? `${num(g.community.players)} jucători azi, ${num(g.community.solvers)} l-au ghicit.` : ''}</p></div>
        <div class="row">
          <button class="btn btn-ghost btn-sm" type="button" data-help>${icon('info')} Reguli</button>
          ${g.stats ? html`<button class="btn btn-ghost btn-sm" type="button" data-stats>${icon('trophy')} Statistici</button>` : ''}
        </div>
      </div>
      ${g.requiresLogin ? loginPrompt('Intră în cont ca să joci Tech Wordle și să câștigi AP.', '/wordle') : ''}
      <div class="wordle">
        <div class="wd-rewards" aria-label="Recompense în funcție de încercare">${join(g.rewards || [], (r, i) => html`<span class="tag ${!done && i === g.guesses.length ? 'is-next' : ''}">${i + 1}: ${r} AP</span>`)}</div>
        ${g.topicHint && !done ? html`<div class="callout">${icon('sparkles')}<span>Indiciu: <strong>${g.topicHint}</strong></span></div>` : ''}
        <div class="wd-board" role="grid" aria-label="Tabla de joc" id="wd-board">${board()}</div>
        <p class="sr-only" aria-live="assertive" id="wd-live"></p>
        ${done ? resultPanel() : kbd()}
      </div>
    </div>`);
  }

  function resultPanel() {
    const won = g.status === 'WON';
    return html`<section class="card card-pad stack center" aria-live="polite">
      <div class="big-emoji" aria-hidden="true">${won ? '🏆' : '🧩'}</div>
      <h2>${won ? `Ai ghicit din ${g.guesses.length}${g.guesses.length === 1 ? ' — legendar!' : '!'}` : 'Nu de data asta'}</h2>
      ${g.answer ? html`<p>Cuvântul era <strong class="mono">${g.answer}</strong>${g.hint ? html` — <span class="muted">${g.hint}</span>` : ''}</p>` : ''}
      <div class="row center-row">
        ${g.shareText ? html`<button class="btn btn-primary" type="button" data-share>${icon('share')} Distribuie rezultatul</button>` : ''}
        <button class="btn btn-ghost" type="button" data-stats>${icon('trophy')} Statistici</button>
      </div>
      <p class="small muted">Cuvânt nou în ${countdownEl(g.resetsAt)}</p>
    </section>`;
  }

  function statsContent() {
    const s = g.stats || { played: 0, wins: 0, winRate: 0, currentStreak: 0, maxStreak: 0, distribution: [] };
    const max = Math.max(1, ...(s.distribution || []));
    const lastWin = g.status === 'WON' ? g.guesses.length - 1 : -1;
    return html`<div class="kpis">
        <div class="kpi"><span class="kpi-value">${num(s.played)}</span><span class="kpi-label">jucate</span></div>
        <div class="kpi"><span class="kpi-value">${num(s.winRate)}%</span><span class="kpi-label">câștigate</span></div>
        <div class="kpi"><span class="kpi-value">${num(s.currentStreak)}</span><span class="kpi-label">serie curentă</span></div>
        <div class="kpi"><span class="kpi-value">${num(s.maxStreak)}</span><span class="kpi-label">serie maximă</span></div>
      </div>
      <h3>Distribuția încercărilor</h3>
      <div class="dist-bars">${join(s.distribution || [], (n, i) => html`<div class="dist-bar ${i === lastWin ? 'is-last' : ''}"><strong>${i + 1}</strong><span data-vars="${vars({ '--p': `${Math.max(7, percent(n, max))}%` })}">${n}</span></div>`)}</div>`;
  }

  function showStats() {
    modal({
      title: `Statistici Tech Wordle`,
      content: statsContent(),
      footer: g.shareText ? html`<button class="btn btn-primary" type="button" data-share-modal>${icon('share')} Distribuie</button>` : null,
      onMount: (dlg) => qs('[data-share-modal]', dlg)?.addEventListener('click', share),
    });
  }

  function share() {
    if (!g.shareText) return;
    const text = `${g.shareText}\nhttps://shop.micutu.com/wordle`;
    if (navigator.share) shareLink({ title: 'Tech Wordle', text: g.shareText, url: 'https://shop.micutu.com/wordle' });
    else copyText(text, 'Rezultat copiat. Lipește-l unde vrei!');
  }

  function shake(message) {
    const row = qsa('.wd-row', root)[g.guesses.length];
    if (row) {
      row.classList.remove('is-shake');
      void row.offsetWidth;
      row.classList.add('is-shake');
    }
    if (message) toast(message, { icon: '🤔', timeout: 2200 });
  }

  function updateActiveRow() {
    const row = qsa('.wd-row', root)[g.guesses.length];
    if (!row) return;
    mount(row, tiles(g.guesses.length));
  }

  async function submit() {
    if (sending || g.status !== 'PLAYING') return;
    if (!isAuthed()) {
      toast('Intră în cont ca să joci.', { icon: '🔐' });
      return;
    }
    if (buffer.length < L) {
      shake(`Cuvântul are ${L} litere.`);
      return;
    }
    sending = true;
    try {
      const r = await post('/games/wordle/guess', { word: buffer });
      if (!alive()) return;
      const prevKeyboard = keyboard;
      g = r;
      buffer = '';
      revealRow = g.guesses.length - 1;
      keyboard = prevKeyboard; // culorile tastaturii se actualizeaza dupa animatie
      render();
      const live = qs('#wd-live', root);
      if (live) live.textContent = r.message || '';
      const wait = reduced() ? 50 : REVEAL_MS * (L - 1) + 520;
      setTimeout(async () => {
        if (!alive()) return;
        keyboard = { ...(g.keyboard || {}) };
        revealRow = -1;
        if (g.status === 'WON') {
          qsa('.wd-row', root)[g.guesses.length - 1]?.classList.add('is-winning');
        }
        if (g.status === 'PLAYING') {
          // doar tastatura si randul urmator se schimba
          const k = qs('.wd-kbd', root);
          if (k) k.outerHTML = kbd().s;
          toast(r.message, { icon: '🔤', timeout: 1800 });
        } else {
          const winRow = g.status === 'WON' ? g.guesses.length - 1 : -1;
          render();
          if (winRow >= 0) qsa('.wd-row', root)[winRow]?.classList.add('is-winning');
          if (g.status === 'WON') toast(r.message, { type: 'reward', icon: '🏆', title: r.rewardPoints ? `+${num(r.rewardPoints)} AP` : null });
          else toast(r.message, { icon: '🧩' });
          await celebrate(r, { confettiOn: g.status === 'WON' });
          setTimeout(() => { if (alive()) showStats(); }, reduced() ? 0 : 1400);
        }
      }, wait);
    } catch (err) {
      if (isAbort(err)) return;
      if (['INVALID_WORD', 'DUPLICATE_GUESS'].includes(err.code)) shake(err.message);
      else if (err.code === 'GAME_OVER') {
        handleError(err);
        g = await get('/games/wordle', { signal }).catch(() => g);
        render();
      } else handleError(err);
    } finally {
      sending = false;
    }
  }

  function press(k) {
    if (g.status !== 'PLAYING' || sending) return;
    if (k === 'ENTER') return submit();
    if (k === 'BACK') {
      buffer = buffer.slice(0, -1);
      updateActiveRow();
      return undefined;
    }
    if (/^[A-Z]$/.test(k) && buffer.length < L) {
      if (!isAuthed()) {
        toast('Intră în cont ca să joci.', { icon: '🔐' });
        return undefined;
      }
      buffer += k;
      updateActiveRow();
    }
    return undefined;
  }

  const onKey = (e) => {
    if (e.ctrlKey || e.metaKey || e.altKey || document.querySelector('dialog[open]')) return;
    if (/^(INPUT|TEXTAREA|SELECT)$/.test(document.activeElement?.tagName)) return;
    const k = e.key;
    if (k === 'Enter') { e.preventDefault(); press('ENTER'); }
    else if (k === 'Backspace') { e.preventDefault(); press('BACK'); }
    else if (/^[a-zA-Z]$/.test(k)) press(k.toUpperCase());
  };
  document.addEventListener('keydown', onKey);

  render();
  const offs = [
    on(root, 'click', '[data-key]', (e, b) => { b.blur(); press(b.dataset.key); }),
    on(root, 'click', '[data-stats]', () => showStats()),
    on(root, 'click', '[data-share]', () => share()),
    on(root, 'click', '[data-help]', () => modal({
      title: 'Cum se joacă',
      content: html`<p>Ghicește cuvântul tech al zilei în ${MAX} încercări. Fiecare încercare trebuie să fie un cuvânt de ${L} litere.</p>
        <div class="wd-row"><div class="wd-tile" data-state="HIT">J</div><div class="wd-tile">A</div><div class="wd-tile">V</div><div class="wd-tile">A</div><div class="wd-tile">X</div></div>
        <p class="small"><strong>Verde</strong>: litera e în cuvânt, pe poziția corectă.</p>
        <div class="wd-row"><div class="wd-tile">C</div><div class="wd-tile" data-state="NEAR">L</div><div class="wd-tile">O</div><div class="wd-tile">U</div><div class="wd-tile">D</div></div>
        <p class="small"><strong>Portocaliu</strong>: litera e în cuvânt, dar pe altă poziție.</p>
        <div class="wd-row"><div class="wd-tile">S</div><div class="wd-tile">T</div><div class="wd-tile" data-state="MISS">A</div><div class="wd-tile">C</div><div class="wd-tile">K</div></div>
        <p class="small"><strong>Gri</strong>: litera nu apare în cuvânt.</p>
        <p class="small muted">Recompensa scade cu fiecare încercare: ${join(g.rewards || [], (r, i) => html`${i ? ' · ' : ''}${i + 1} → ${apAmount(r)}`)}. După 3 încercări primești un indiciu. Un cuvânt nou apare zilnic la miezul nopții.</p>`,
    })),
    on(root, 'countdown:end', '[data-countdown]', () => setTimeout(async () => {
      if (!alive()) return;
      g = await get('/games/wordle', { signal }).catch(() => g);
      keyboard = { ...(g.keyboard || {}) };
      buffer = '';
      render();
    }, 1500)),
  ];
  return () => {
    document.removeEventListener('keydown', onKey);
    offs.forEach((off) => off());
  };
}

function label(state) {
  return { HIT: 'poziție corectă', NEAR: 'în cuvânt, altă poziție', MISS: 'nu apare' }[state] || state;
}
