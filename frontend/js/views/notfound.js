import { html, mount } from '../dom.js';
import { setMeta } from '../seo.js';

export default function notfound({ root }) {
  setMeta({ title: 'Pagina nu a fost găsită', noindex: true });
  mount(root, html`<section class="notfound">
    <div class="code" aria-hidden="true">404</div>
    <h1>Pagina nu a fost găsită</h1>
    <a class="btn btn-primary" href="/magazin">Explorează magazinul</a>
  </section>`);
}
