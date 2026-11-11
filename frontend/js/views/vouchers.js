import { post } from '../api.js';
import { html, mount, on, qs } from '../dom.js';
import { dateTime } from '../format.js';
import { setMeta } from '../seo.js';
import { busy } from '../ui.js';

const STATES = { ACTIVE: 'Voucher activ', REDEEMED: 'Voucher deja folosit', CANCELLED: 'Voucher anulat' };

export default function vouchers({ root, alive }) {
  setMeta({ title: 'Verifică un voucher', noindex: true });
  mount(root, html`<section class="stack narrow">
    <div><h1>Verifică un voucher</h1><p class="muted">Introdu codul pentru a vedea recompensa și starea voucherului.</p></div>
    <form class="card card-pad stack">
      <label class="field" for="voucher-code">Cod voucher
        <input class="input" id="voucher-code" name="code" maxlength="40" minlength="8" placeholder="GT-XXXX-XXXX-XXXX" autocomplete="off" autocapitalize="characters" spellcheck="false" required>
      </label>
      <div><button class="btn btn-primary" type="submit">Verifică voucherul</button></div>
    </form>
    <div id="voucher-result" role="status" aria-live="polite"></div>
  </section>`);
  return on(root, 'submit', 'form', (event, form) => {
    event.preventDefault();
    busy(form.querySelector('[type="submit"]'), async () => {
      const result = qs('#voucher-result', root);
      result.replaceChildren();
      try {
        const voucher = await post('/vouchers/verify', { code: form.elements.code.value.trim() }, { auth: false });
        if (!alive()) return;
        mount(result, html`<article class="card card-pad stack">
          <h2>${STATES[voucher.state] || voucher.state}</h2><strong>${voucher.rewardName}</strong>
          <span>${voucher.quantity} ${voucher.quantity === 1 ? 'bucată' : 'bucăți'}</span>
          <span class="muted">Emis ${dateTime(voucher.issuedAt)}</span>
        </article>`);
      } catch (error) {
        if (alive()) mount(result, html`<p class="callout is-danger">${error.message}</p>`);
      }
    });
  });
}
