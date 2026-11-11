import { html, mount, join, on } from './dom.js';
import { get, del, enc } from './api.js';
import { dateTime } from './format.js';
import { icon } from './icons.js';
import { state } from './store.js';
import { busy, confirmDialog, handleError, toastOk } from './ui.js';

export async function sessionSettings(root, { signal, alive }) {
  let disposed = false;
  let generation = 0;

  async function refresh() {
    const request = ++generation;
    const token = state.token;
    const data = await get('/auth/sessions', { signal });
    if (disposed || !alive() || request !== generation || state.token !== token) return;
    mount(root, html`<h2 class="card-title">${icon('shield')} Sesiuni active</h2>
      <p class="small muted">Maximum ${data.max} autentificări simultane. O autentificare nouă închide cea mai veche sesiune dacă ai atins limita. Dispozitivul este estimat din browser.</p>
      <div class="stack">${join(data.items || [], (s) => html`<article class="stack stack-sm">
        <div class="row row-between"><strong>${s.device}</strong>${s.current ? html`<span class="tag tag-brand">Sesiunea curentă</span>` : ''}</div>
        <p class="small muted">Conectat ${dateTime(s.createdAt)}<br>Ultima utilizare ${dateTime(s.lastSeenAt)}<br>Expiră ${dateTime(s.expiresAt)}</p>
        ${s.current ? '' : html`<button class="btn btn-secondary btn-sm" type="button" data-revoke-session="${s.id}" aria-label="Deconectează ${s.device}">${icon('logout')} Deconectează sesiunea</button>`}
      </article>`)}</div>`);
  }

  const off = on(root, 'click', '[data-revoke-session]', async (e, b) => {
    if (!(await confirmDialog({ title: 'Închizi această sesiune?', body: 'Pe acel dispozitiv va fi necesară o nouă autentificare. Sesiunea de aici rămâne deschisă.', confirmText: 'Deconectează' }))) return;
    await busy(b, async () => {
      try {
        await del(`/auth/sessions/${enc(b.dataset.revokeSession)}`);
        if (disposed || !alive()) return;
        toastOk('Sesiunea a fost închisă.');
        await refresh();
      } catch (err) { if (!disposed && alive()) handleError(err); }
    });
  });

  try { await refresh(); } catch (err) {
    off();
    throw err;
  }
  return { refresh, cleanup: () => { disposed = true; off(); } };
}
