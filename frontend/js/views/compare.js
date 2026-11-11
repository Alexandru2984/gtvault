import { get } from '../api.js';
import { apAmount, emptyState, FULFILLMENT, ratingInline } from '../components.js';
import { compareLink, comparedIds, setCompared } from '../compare.js';
import { html, join, mount, on, productImage } from '../dom.js';
import { navigate } from '../router.js';
import { setMeta } from '../seo.js';
import { shareLink } from '../ui.js';

export default async function compare({ root, query, signal, alive }) {
  const ids = query.ids === undefined ? comparedIds() : comparedIds(query.ids.split(','));
  setCompared(ids);
  setMeta({ title: 'Compară premii', noindex: true });
  const results = await Promise.all(ids.map(async (id) => {
    try { return (await get(`/rewards/${encodeURIComponent(id)}`, { signal })).reward; }
    catch (error) { if (error.status === 404) return null; throw error; }
  }));
  if (!alive()) return;
  const rewards = results.filter(Boolean);
  const rows = [
    ['Preț', (reward) => apAmount(reward.deal?.dealPrice ?? reward.price)],
    ['Categorie', (reward) => reward.category],
    ['Livrare', (reward) => FULFILLMENT[reward.fulfillment]?.label || reward.fulfillment],
    ['Disponibilitate', (reward) => reward.inStock ? 'În stoc' : 'Epuizat'],
    ['Recenzii', (reward) => ratingInline(reward.rating, reward.reviewCount)],
  ];
  mount(root, html`<section class="page">
    <div class="page-head"><div><h1>Compară premii</h1><p>Alege până la 3 premii din magazin.</p></div>
      ${rewards.length ? html`<button class="btn btn-secondary" type="button" data-share-compare>Distribuie comparația</button>` : ''}</div>
    ${ids.length > rewards.length ? html`<p class="callout">Unele premii din selecție au fost retrase din catalog.</p>` : ''}
    ${rewards.length ? html`<div class="table-wrap compare-table" tabindex="0" role="region" aria-label="Comparația premiilor">
      <table class="table"><caption class="sr-only">Preț, livrare, stoc și recenzii pentru premiile selectate</caption><thead>
        <tr><th scope="col">Premiu</th>${join(rewards, (reward) => html`<th scope="col" class="wrap"><a href="/produs/${encodeURIComponent(reward.id)}"><img src="${productImage(reward)}" alt="" width="96" height="96"><br>${reward.name}</a></th>`)}</tr>
      </thead><tbody>
        ${join(rows, ([label, value]) => html`<tr><th scope="row">${label}</th>${join(rewards, (reward) => html`<td class="wrap">${value(reward)}</td>`)}</tr>`)}
        <tr><th scope="row">Acțiuni</th>${join(rewards, (reward) => html`<td><div class="stack">
          ${reward.inStock ? html`<button class="btn btn-primary" type="button" data-add-cart="${reward.id}">Adaugă în coș</button>` : ''}
          <button class="btn btn-ghost" type="button" data-remove-compare="${reward.id}" aria-label="Scoate ${reward.name} din comparație">Elimină</button>
        </div></td>`)}</tr>
      </tbody></table></div>` : emptyState({ title: 'Alege premiile pe care vrei să le compari', action: html`<a class="btn btn-primary" href="/magazin">Mergi în magazin</a>` })}
  </section>`);
  const remove = on(root, 'click', '[data-remove-compare]', (event, button) => {
    navigate(compareLink(setCompared(ids.filter((id) => id !== button.dataset.removeCompare))));
  });
  const share = on(root, 'click', '[data-share-compare]', () => shareLink({
    title: 'Compară premii RewardHub', text: 'Ce premiu ai alege?', url: location.origin + compareLink(ids),
  }));
  return () => { remove(); share(); };
}
