import { html, mount, join, on, qs } from './dom.js';
import { get, put, isAbort } from './api.js';
import { state } from './store.js';
import { busy, handleError } from './ui.js';

const OPTIONS = [
  ['wishlistSales', 'Reduceri la favorite', 'Când un premiu salvat intră într-o ofertă flash.'],
  ['wishlistStock', 'Reveniri în stoc', 'Când poți comanda din nou un premiu salvat.'],
  ['progress', 'Progres și realizări', 'Insigne, misiuni și praguri de streak.'],
  ['community', 'Activitate în comunitate', 'Voturi utile la recenzii și voturi la propuneri.'],
  ['announcements', 'Noutăți RewardHub', 'Anunțuri publicate de echipa platformei.'],
];

export async function notificationSettings(root, { signal, alive, open = false }) {
  let disposed = false;
  async function load() {
    const token = state.token;
    try {
      const data = await get('/notifications/preferences', { signal });
      if (disposed || !alive() || state.token !== token) return;
      mount(root, html`<details class="card card-pad notification-options" ${open ? 'open' : ''}>
        <summary>Preferințe de notificare</summary>
        <form class="stack mt-2" data-notification-preferences>
          <p class="small muted">Alege alertele pe care vrei să le vezi în aplicație. Preferințele se păstrează în cont și se aplică notificărilor viitoare, pe toate dispozitivele.</p>
          <fieldset class="stack preference-options"><legend class="sr-only">Categorii de notificări opționale</legend>
            ${join(OPTIONS, ([key, title, text]) => html`<label class="switch">
              <input type="checkbox" name="${key}" ${data.preferences[key] ? 'checked' : ''}>
              <span>${title}<span class="hint">${text}</span></span>
            </label>`)}
          </fieldset>
          <p class="small muted">Mesajele despre cont, comenzi, cadouri, bonusuri de recomandare și decizii de moderare rămân active. Notificările existente nu sunt șterse.</p>
          <div><button class="btn btn-primary" type="submit">Salvează preferințele</button></div>
          <p class="small" role="status" data-preferences-status hidden></p>
        </form>
      </details>`);
    } catch (err) {
      if (disposed || !alive() || isAbort(err)) return;
      mount(root, html`<section class="card card-pad stack"><p>Preferințele de notificare nu au putut fi încărcate.</p><button class="btn btn-secondary" type="button" data-retry-preferences>Reîncearcă</button></section>`);
    }
  }

  const offs = [
    on(root, 'click', '[data-retry-preferences]', () => load()),
    on(root, 'submit', '[data-notification-preferences]', async (e, form) => {
      e.preventDefault();
      const fieldset = qs('fieldset', form);
      if (fieldset.disabled) return;
      const token = state.token;
      const preferences = Object.fromEntries(OPTIONS.map(([key]) => [key, form.elements[key].checked]));
      const status = qs('[data-preferences-status]', form);
      status.hidden = true;
      fieldset.disabled = true;
      await busy(qs('[type=submit]', form), async () => {
        try {
          await put('/notifications/preferences', preferences);
          if (disposed || !alive() || state.token !== token) return;
          status.textContent = 'Preferințele au fost salvate.';
          status.hidden = false;
        } catch (err) { if (!disposed && alive() && state.token === token) handleError(err); }
        finally { fieldset.disabled = false; }
      });
    }),
  ];
  await load();
  return () => { disposed = true; offs.forEach((off) => off()); };
}
