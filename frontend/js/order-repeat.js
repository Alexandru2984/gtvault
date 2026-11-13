import { html, mount, join, qs, on } from './dom.js';
import { get, post, enc, isAbort } from './api.js';
import { apAmount } from './components.js';
import { num, plural } from './format.js';
import { setCartCount } from './store.js';
import { navigate } from './router.js';
import { busy, handleError, toastOk } from './ui.js';

export async function repeatOrder({ orderId, signal, active, openDialog }) {
  const path = `/cart/repeat-order/${enc(orderId)}`;
  let preview;
  try {
    preview = await get(path, { signal });
    if (!active()) return;
  } catch (err) { if (active() && !isAbort(err)) handleError(err); return; }
  await openDialog({
    title: 'Adaugă din nou în coș',
    content: html`<div data-repeat-body>${content(preview)}</div>`,
    footer: html`<a class="btn btn-ghost" href="/cos">Vezi coșul</a><button class="btn btn-primary" type="button" data-repeat-confirm ${preview.canAdd ? '' : 'disabled'}>Adaugă în coș</button>`,
    onMount: (dlg, close) => {
      const controller = new AbortController();
      let open = true;
      let sending = false;
      const abort = () => controller.abort();
      signal.addEventListener('abort', abort, { once: true });
      const current = () => open && active() && !controller.signal.aborted;
      const body = qs('[data-repeat-body]', dlg);
      const confirm = qs('[data-repeat-confirm]', dlg);
      dlg.addEventListener('close', () => { open = false; abort(); signal.removeEventListener('abort', abort); }, { once: true });
      on(dlg, 'click', '[data-repeat-confirm]', (event, button) => {
        if (!current() || sending || !preview.canAdd) return;
        sending = true;
        busy(button, async () => {
          try {
            const result = await post(path, { version: preview.version }, { signal: controller.signal });
            if (!current()) return;
            setCartCount(result.quote.itemCount);
            toastOk(result.message);
            close(true);
            navigate('/cos');
          } catch (err) {
            if (!current() || isAbort(err)) return;
            if (err.code === 'REPEAT_CHANGED' || err.code === 'REPEAT_UNAVAILABLE') {
              try {
                const fresh = await get(path, { signal: controller.signal });
                if (!current()) return;
                preview = fresh;
                mount(body, html`<p class="callout is-warning" role="alert">${err.message} Dacă ai mai trimis cererea, verifică întâi cantitățile din coș.</p>${content(preview)}`);
              } catch (refreshError) {
                if (!current() || isAbort(refreshError)) return;
                preview = { ...preview, canAdd: false };
                handleError(refreshError);
              }
            } else handleError(err);
          } finally {
            sending = false;
            // Aplicăm starea noii previzualizări după ce busy() reactivează butonul.
          }
        }).then(() => { if (current()) confirm.disabled = !preview.canAdd; });
      });
    },
  });
}

function content(preview) {
  const q = preview.quote;
  return html`<div class="stack repeat-preview">
    <p>Adăugăm cantitățile din comanda veche la produsele deja alese. Confirmarea pregătește coșul și nu cheltuie AP.</p>
    <div class="stack stack-sm">${join(preview.items, (item) => html`<div class="repeat-item">
      <strong>${q?.lines.find((line) => line.rewardId === item.rewardId)?.name || item.name || 'Produs neidentificat'}</strong>
      <div class="small muted">De adăugat: ${num(item.quantity)} · deja în coș: ${num(item.inCart)} · după adăugare: ${num(item.afterAddition)}</div>
    </div>`)}</div>
    ${preview.issues.length ? html`<div class="callout is-warning" role="alert"><div><strong>Comanda nu poate fi repetată integral.</strong><ul>${join(preview.issues, (issue) => html`<li>${issue}</li>`)}</ul><p class="small">Coșul rămâne neschimbat. Ajustează produsele sau cantitățile înainte de a încerca din nou.</p></div></div>` : ''}
    ${q ? html`<details ${q.lines.length <= 3 ? 'open' : ''}><summary class="strong">Coșul după adăugare · ${plural(q.itemCount, 'produs', 'produse')}</summary><ul class="repeat-quote-list mt-1">${join(q.lines, (line) => html`<li><span>${line.quantity}× ${line.name}</span><span>${num(line.lineTotal)} AP</span></li>`)}</ul></details>
      <dl class="dl"><dt>Subtotal</dt><dd>${num(q.subtotal)} AP</dd>${q.flashDiscount ? html`<dt>Oferte actuale</dt><dd class="discount">−${num(q.flashDiscount)} AP</dd>` : ''}${q.tierDiscount ? html`<dt>Reducere de nivel</dt><dd class="discount">−${num(q.tierDiscount)} AP</dd>` : ''}<dt class="total">Total estimat al coșului</dt><dd class="total">${apAmount(q.total)}</dd></dl>
      ${q.missingPoints > 0 ? html`<p class="small muted">Îți lipsesc ${num(q.missingPoints)} AP pentru finalizare. Poți păstra produsele în coș cât timp strângi punctele.</p>` : ''}` : ''}
    <p class="small muted">Se folosesc prețurile și ofertele actuale. Stocul nu se rezervă, iar codul promo al comenzii vechi nu se copiază. Prețul se verifică din nou la finalizare.</p>
  </div>`;
}
