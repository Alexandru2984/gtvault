import { html, mount, join, on, safeHref } from '../dom.js';
import { get, post, del, enc, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { timeAgo, plural } from '../format.js';
import { emptyState, skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { state, setUnread } from '../store.js';
import { busy, confirmDialog, handleError } from '../ui.js';
import { notificationSettings } from '../notification-settings.js';

export default async function notifications({ root, query, signal, alive }) {
  setMeta({ title: 'Notificări', noindex: true });
  mount(root, skeletonPage());
  const token = state.token;
  const active = () => alive() && state.token === token;
  let data;
  async function load() {
    data = await get('/notifications?limit=50', { signal });
    if (active()) setUnread(data.unread);
  }
  try {
    await load();
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!active()) return;

  mount(root, html`<div class="page stack-lg"><div data-notification-feed></div><div data-notification-options></div></div>`);
  const feed = root.querySelector('[data-notification-feed]');
  function render() {
    const items = data.items || [];
    mount(feed, html`<div class="stack">
      <div class="page-head">
        <div><h1>Notificări</h1><p>${data.unread ? plural(data.unread, 'notificare necitită', 'notificări necitite') : 'Ești la zi cu tot. 👌'}</p></div>
        ${items.length ? html`<div class="row">
          ${data.unread ? html`<button class="btn btn-secondary btn-sm" type="button" data-all-read>${icon('check')} Marchează tot ca citit</button>` : ''}
          <button class="btn btn-ghost btn-sm" type="button" data-clear>${icon('trash')} Șterge tot</button>
        </div>` : ''}
      </div>
      ${items.length ? html`<section class="card">${join(items, item)}</section>`
        : emptyState({ icon: '🔔', title: 'Nicio notificare', text: 'Aici apar comenzile, insignele, cadourile, alertele de preț și anunțurile comunității.' })}
    </div>`);
  }

  function item(n) {
    return html`<div class="notif ${n.read ? '' : 'is-unread'}">
      <span class="notif-ico" aria-hidden="true">${n.icon || '🔔'}</span>
      <a class="notif-main" href="${n.link ? safeHref(n.link) : '#'}" data-open="${n.id}" data-read="${n.read}">
        <div class="notif-title">${n.title}</div>
        ${n.body ? html`<div class="notif-body">${n.body}</div>` : ''}
        <div class="notif-at">${timeAgo(n.createdAt)}</div>
      </a>
      <button class="btn btn-ghost btn-sm btn-icon" type="button" data-delete="${n.id}" aria-label="Șterge notificarea">${icon('x')}</button>
    </div>`;
  }

  render();

  const preferencesReady = notificationSettings(root.querySelector('[data-notification-options]'), {
    signal, alive: active, open: query.preferinte === '1',
  });

  const offs = [
    on(root, 'click', '[data-open]', (e, a) => {
      const n = data.items.find((x) => x.id === a.dataset.open);
      if (a.getAttribute('href') === '#') e.preventDefault();
      if (n && !n.read) {
        n.read = true;
        data.unread = Math.max(0, data.unread - 1);
        setUnread(data.unread);
        post(`/notifications/${enc(n.id)}/read`).catch(() => {});
        if (a.getAttribute('href') === '#') render();
      }
    }),
    on(root, 'click', '[data-delete]', async (e, b) => busy(b, async () => {
      try {
        await del(`/notifications/${enc(b.dataset.delete)}`);
        if (!active()) return;
        const n = data.items.find((x) => x.id === b.dataset.delete);
        if (n && !n.read) data.unread = Math.max(0, data.unread - 1);
        data.items = data.items.filter((x) => x.id !== b.dataset.delete);
        setUnread(data.unread);
        render();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-all-read]', (e, b) => busy(b, async () => {
      try {
        await post('/notifications/read-all');
        if (!active()) return;
        data.items.forEach((x) => { x.read = true; });
        data.unread = 0;
        setUnread(0);
        render();
      } catch (err) { handleError(err); }
    })),
    on(root, 'click', '[data-clear]', async (e, b) => {
      if (!(await confirmDialog({ title: 'Ștergi toate notificările?', body: 'Lista va fi golită definitiv.', confirmText: 'Șterge tot', danger: true }))) return;
      await busy(b, async () => {
        try {
          await del('/notifications');
          if (!active()) return;
          data = { unread: 0, items: [] };
          setUnread(0);
          render();
        } catch (err) { handleError(err); }
      });
    }),
  ];
  return () => { preferencesReady.then((cleanup) => cleanup()); offs.forEach((off) => off()); };
}
