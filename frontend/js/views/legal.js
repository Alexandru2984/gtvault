import { html, mount } from '../dom.js';
import { setMeta } from '../seo.js';

export default function legal({ root }) {
  const privacy = location.pathname.startsWith('/confidentialitate');
  const title = privacy ? 'Confidențialitate' : 'Reguli de utilizare';
  setMeta({ title });
  mount(root, html`<article class="stack narrow">
    <h1>${title}</h1>
    ${privacy ? html`
      <section class="card card-pad stack">
        <h2>Date folosite în RewardHub</h2>
        <p>Contul include adresa de email, numele de afișare și activitatea din aplicație. Pentru comenzile fizice sunt necesare date de livrare.</p>
        <h2>Profil și activitate publică</h2>
        <p>Poți seta vizibilitatea profilului din setări. Recenziile, propunerile și clasamentele pot afișa numele de afișare separat de pagina de profil.</p>
        <h2>Opțiunile contului</h2>
        <p>Din <a href="/profil?tab=setari">setări</a> poți descărca datele contului, gestiona sesiunile și cere ștergerea contului.</p>
      </section>` : html`
      <section class="card card-pad stack">
        <h2>Puncte și recompense</h2>
        <p>Folosești punctele AP pentru recompensele disponibile în magazin. Înainte de confirmare, verifică prețul, stocul și datele de livrare.</p>
        <h2>Participarea în comunitate</h2>
        <p>Publică recenzii despre experiența ta cu produsul și respectă ceilalți membri. Poți raporta o recenzie pentru verificare.</p>
        <h2>Comenzi și vouchere</h2>
        <p>Urmărește statusul, opțiunile de anulare și codurile digitale în <a href="/comenzi">Comenzile mele</a>.</p>
      </section>`}
    <p><a href="/ajutor">Întrebări frecvente</a></p>
  </article>`);
}
