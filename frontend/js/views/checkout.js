import { html, mount, join, qs, qsa, on, productImage, formData } from '../dom.js';
import { get, post, newIdempotencyKey, isAbort } from '../api.js';
import { icon } from '../icons.js';
import { num } from '../format.js';
import { skeletonPage } from '../components.js';
import { setMeta } from '../seo.js';
import { navigate } from '../router.js';
import { setCartCount, setBalance, emit } from '../store.js';
import { busy, celebrate, confetti, handleError } from '../ui.js';
import { fetchQuote, storedPromo, storePromo, summary } from './cart.js';

export const COUNTIES = ['Alba', 'Arad', 'Argeș', 'Bacău', 'Bihor', 'Bistrița-Năsăud', 'Botoșani', 'Brașov', 'Brăila', 'București',
  'Buzău', 'Caraș-Severin', 'Călărași', 'Cluj', 'Constanța', 'Covasna', 'Dâmbovița', 'Dolj', 'Galați', 'Giurgiu', 'Gorj',
  'Harghita', 'Hunedoara', 'Ialomița', 'Iași', 'Ilfov', 'Maramureș', 'Mehedinți', 'Mureș', 'Neamț', 'Olt', 'Prahova',
  'Satu Mare', 'Sălaj', 'Sibiu', 'Suceava', 'Teleorman', 'Timiș', 'Tulcea', 'Vaslui', 'Vâlcea', 'Vrancea'];

const KEY_STORE = 'gt.checkout.key';

/** Cheia de idempotenta ramane aceeasi pana la reusita, deci un retry/dublu-click nu dubleaza comanda. */
function idemKey() {
  try {
    let k = sessionStorage.getItem(KEY_STORE);
    if (!k) { k = newIdempotencyKey(); sessionStorage.setItem(KEY_STORE, k); }
    return k;
  } catch { return newIdempotencyKey(); }
}
function resetKey() {
  try { sessionStorage.removeItem(KEY_STORE); } catch { /* ignorat */ }
}

const PHONE = /^(\+40|0040|0)7\d{8}$/;

export function validateShipping(s) {
  const errors = {};
  if (!s.name || s.name.length < 3 || /[<>]/.test(s.name)) errors.name = 'Numele complet (minimum 3 caractere).';
  if (!PHONE.test((s.phone || '').replace(/[\s().-]/g, ''))) errors.phone = 'Un număr de mobil românesc valid, ex: 0722 123 456.';
  if (!s.address || s.address.length < 5) errors.address = 'Strada, numărul, blocul, apartamentul.';
  if (!s.city || s.city.length < 2) errors.city = 'Localitatea.';
  if (!COUNTIES.includes(s.county)) errors.county = 'Selectează județul.';
  if (!/^\d{6}$/.test(s.postalCode || '')) errors.postalCode = 'Codul poștal are 6 cifre.';
  return errors;
}

export default async function checkout({ root, signal, alive }) {
  setMeta({ title: 'Finalizează comanda', noindex: true });
  mount(root, skeletonPage());
  let q;
  let last = null;
  try {
    q = await fetchQuote(signal);
    if (q.requiresShipping) last = await get('/orders/last-shipping', { signal }).catch(() => null);
  } catch (err) {
    if (isAbort(err)) return;
    throw err;
  }
  if (!alive()) return;
  if (!q.lines?.length) {
    navigate('/cos', { replace: true });
    return;
  }

  const ship = last || {};
  const blocked = q.issues?.length > 0 || q.missingPoints > 0;
  mount(root, html`<div class="page">
    <div class="page-head">
      <div>
        <div class="checkout-steps"><a href="/cos">Coș</a>${icon('chevronRight')}<span class="is-active">Detalii comandă</span>${icon('chevronRight')}<span>Confirmare</span></div>
        <h1 class="mt-1">Finalizează comanda</h1>
      </div>
    </div>
    ${blocked ? html`<div class="callout is-danger">${icon('alert')}<div>${q.missingPoints > 0 ? `Îți lipsesc ${num(q.missingPoints)} AP. ` : ''}${join(q.issues || [], (i) => html`<div>${i}</div>`)} <a href="/cos">Înapoi la coș</a></div></div>` : ''}
    <form class="cart-layout" id="checkout-form" novalidate>
      <div class="stack stack-lg">
        ${q.requiresShipping ? html`<section class="card card-pad stack" aria-labelledby="ship-h">
          <h2 class="card-title" id="ship-h">${icon('truck')} Livrare</h2>
          <p class="small muted">Produsele fizice se livrează prin curier, gratuit. ${last ? 'Am completat datele din ultima ta comandă.' : ''}</p>
          <div class="form-grid">
            ${field('name', 'Nume destinatar', ship.name, { autocomplete: 'name', maxlength: 80 })}
            ${field('phone', 'Telefon mobil', ship.phone, { autocomplete: 'tel', type: 'tel', inputmode: 'tel', maxlength: 16, placeholder: '07xx xxx xxx' })}
            ${field('address', 'Adresă', ship.address, { autocomplete: 'street-address', maxlength: 200, span: true, placeholder: 'Strada, nr., bloc, scară, apartament' })}
            ${field('city', 'Localitate', ship.city, { autocomplete: 'address-level2', maxlength: 80 })}
            <div class="field">
              <label for="f-county">Județ</label>
              <select class="select" id="f-county" name="county" autocomplete="address-level1" required>
                <option value="">Alege județul</option>
                ${join(COUNTIES, (c) => html`<option ${c === ship.county ? 'selected' : ''}>${c}</option>`)}
              </select>
              <span class="error" data-err="county" hidden></span>
            </div>
            ${field('postalCode', 'Cod poștal', ship.postalCode, { autocomplete: 'postal-code', inputmode: 'numeric', maxlength: 6, placeholder: '6 cifre' })}
          </div>
        </section>` : html`<div class="callout is-success">${icon('zap')}<span>Comanda conține doar produse digitale: primești codurile de voucher imediat după plasare, în pagina comenzii.</span></div>`}
        <section class="card card-pad stack" aria-labelledby="notes-h">
          <h2 class="card-title" id="notes-h">${icon('message')} Observații <span class="small muted">(opțional)</span></h2>
          <textarea class="textarea" name="notes" maxlength="300" placeholder="${q.requiresShipping ? 'Ex: sunați înainte de livrare, mărimea tricoului (M)…' : 'Ceva ce ar trebui să știm?'}"></textarea>
        </section>
        <section class="card" aria-label="Produse">
          ${join(q.lines, (l) => html`<div class="cart-line">
            <img src="${productImage(l)}" alt="" width="76" height="76" loading="lazy">
            <div class="cart-line-main"><span class="cart-line-name">${l.name}</span><span class="small muted">${l.quantity} × ${num(l.unitPrice)} AP${l.dealUnits ? ` · ${l.dealUnits} la ofertă` : ''}</span><strong>${num(l.lineTotal)} AP</strong></div>
          </div>`)}
        </section>
      </div>
      <aside class="card card-pad stack summary-sticky" aria-labelledby="sum-h">
        <h2 id="sum-h" class="card-title">Sumar</h2>
        <div id="sum-box">${summary(q, { showPromoForm: false })}</div>
        <div class="form-error" id="checkout-err" role="alert" hidden></div>
        <button class="btn btn-primary btn-lg btn-block" type="submit" ${blocked ? 'disabled' : ''}>Plasează comanda · ${num(q.total)} AP</button>
        <p class="xsmall muted center">Plasând comanda accepți <a href="/termeni">termenii</a>. AP-ul se scade la plasare și se returnează integral dacă anulezi înainte de expediere.</p>
      </aside>
    </form>
  </div>`);

  const form = qs('#checkout-form', root);
  const errBox = qs('#checkout-err', root);

  function showErrors(errors) {
    for (const el of qsa('[data-err]', form)) {
      const k = el.dataset.err;
      const input = form.elements[k];
      if (errors[k]) {
        el.textContent = errors[k];
        el.hidden = false;
        input?.setAttribute('aria-invalid', 'true');
      } else {
        el.hidden = true;
        input?.removeAttribute('aria-invalid');
      }
    }
    const first = Object.keys(errors)[0];
    if (first) form.elements[first]?.focus();
  }

  const off = on(root, 'submit', '#checkout-form', async (e) => {
    e.preventDefault();
    errBox.hidden = true;
    const fd = formData(form);
    let shipping = null;
    if (q.requiresShipping) {
      shipping = { name: fd.name, phone: fd.phone, address: fd.address, city: fd.city, county: fd.county, postalCode: fd.postalCode };
      const errors = validateShipping(shipping);
      showErrors(errors);
      if (Object.keys(errors).length) return;
    }
    const btn = qs('[type=submit]', form);
    await busy(btn, async () => {
      try {
        const res = await post('/checkout', {
          promoCode: storedPromo() || null, shipping, notes: fd.notes || null, expectedTotal: q.total,
        }, { idempotencyKey: idemKey() });
        resetKey();
        storePromo('');
        setCartCount(0);
        setBalance(res.newBalance);
        emit('order:placed', res.order);
        confetti();
        await celebrate({ badgesUnlocked: res.badgesUnlocked });
        navigate(`/comenzi/${encodeURIComponent(res.order.id)}?nou=1`, { replace: true });
      } catch (err) {
        if (err.code === 'PRICE_CHANGED' || err.code === 'INSUFFICIENT_POINTS' || err.status === 409) {
          // ceva s-a schimbat (pret, stoc, sold): reincarcam cotatia si cerem o noua confirmare
          resetKey();
          try {
            q = await fetchQuote(signal);
            mount(qs('#sum-box', root), summary(q, { showPromoForm: false }));
            btn.textContent = `Plasează comanda · ${num(q.total)} AP`;
          } catch { /* ignorat */ }
        }
        if (err.status === 400 && err.code !== 'INSUFFICIENT_POINTS') resetKey();
        errBox.textContent = err.message;
        errBox.hidden = false;
        handleError(err);
      }
    });
  });
  return off;
}

function field(name, label, value, { autocomplete = 'off', type = 'text', inputmode = null, maxlength = 100, span = false, placeholder = '' } = {}) {
  return html`<div class="field ${span ? 'span-2' : ''}">
    <label for="f-${name}">${label}</label>
    <input class="input" id="f-${name}" name="${name}" type="${type}" value="${value || ''}" autocomplete="${autocomplete}" ${inputmode ? html`inputmode="${inputmode}"` : ''} maxlength="${maxlength}" placeholder="${placeholder}" required>
    <span class="error" data-err="${name}" hidden></span>
  </div>`;
}

