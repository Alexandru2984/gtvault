import { html, mount, join } from '../dom.js';
import { get, isAbort } from '../api.js';
import { date, plural } from '../format.js';
import { emptyState, productCard, skeletonGrid } from '../components.js';
import { setMeta } from '../seo.js';
import { on as onStore } from '../store.js';

export default async function wishlist({ root, signal, alive }) {
  setMeta({ title: 'Favorite', noindex: true });
  mount(root, html`<div class="page"><h1>Favorite</h1>${skeletonGrid(4)}</div>`);
  let data;
  try {
    data = await get('/wishlist', { signal });
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;

  function render() {
    const items = data.items || [];
    mount(root, html`<div class="page">
      <div class="page-head">
        <div>
          <h1>Favorite</h1>
          <p>${items.length ? `${plural(items.length, 'premiu salvat', 'premii salvate')}${data.onSale ? ` · ${plural(data.onSale, 'e la ofertă acum', 'sunt la ofertă acum')} 🔥` : ''}. Primești notificare când intră la reducere sau revin în stoc.` : 'Salvează premiile care îți plac și te anunțăm când intră la reducere.'}</p>
        </div>
      </div>
      <p class="small muted">Poți alege alertele din <a href="/notificari?preferinte=1">preferințele de notificare</a>.</p>
      ${items.length
        ? html`<div class="products">${join(items, (it) => html`<div class="stack stack-sm">${productCard(it.reward)}<span class="xsmall muted center">Salvat ${date(it.addedAt)}</span></div>`)}</div>`
        : emptyState({ icon: '❤️', title: 'Nicio favorită încă', text: 'Apasă inima de pe orice premiu ca să-l salvezi aici.', action: html`<a class="btn btn-primary" href="/magazin">Descoperă premiile</a>` })}
    </div>`);
  }
  render();

  // scoaterea unei favorite din pagina o elimina din lista
  return onStore('wishlist:toggled', ({ rewardId, wishlisted }) => {
    if (wishlisted || !alive()) return;
    data.items = (data.items || []).filter((it) => it.reward.id !== rewardId);
    render();
  });
}

