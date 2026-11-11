import { html, mount } from '../dom.js';
import { setMeta } from '../seo.js';

export default function legal({ root }) {
  const privacy = location.pathname.startsWith('/confidentialitate');
  const title = privacy ? 'Confidențialitate' : 'Reguli de utilizare';
  setMeta({ title });
  mount(root, legalContent(privacy));
}

export function legalContent(privacy) {
  const title = privacy ? 'Confidențialitate' : 'Reguli de utilizare';
  return html`<article class="stack narrow">
    <h1>${title}</h1>
    <p>Operatorul platformei este <strong>Dragne Alexandru Mihai</strong>. Pentru întrebări despre cont, comenzi sau datele personale, scrie la <a href="mailto:984.alexmihai@proton.me">984.alexmihai@proton.me</a>.</p>
    ${privacy ? html`
      <section class="card card-pad stack">
        <h2>Date folosite în RewardHub</h2>
        <p>Contul include adresa de email, numele de afișare și activitatea din aplicație. Pentru comenzile fizice sunt necesare date de livrare.</p>
        <p>Sesiunile active păstrează browserul și sistemul estimate, data autentificării, ultima utilizare și expirarea. Nu păstrăm IP-ul sau user-agentul complet în lista de sesiuni. Sesiunile expirate sunt eliminate automat zilnic, iar deconectarea confirmată de server elimină sesiunea respectivă.</p>
        <p>Folosim aceste informații pentru autentificare, evidența punctelor, procesarea comenzilor și funcționarea comunității. Traficul trece prin Cloudflare și serverul de găzduire, care procesează date tehnice pentru livrarea și securizarea site-ului.</p>
        <h2>Profil și activitate publică</h2>
        <p>Poți seta vizibilitatea profilului din setări. Recenziile, propunerile și clasamentele pot afișa numele de afișare separat de pagina de profil.</p>
        <h2>Opțiunile contului</h2>
        <p>Din <a href="/profil?tab=setari">setări</a> poți descărca datele contului, gestiona sesiunile și cere ștergerea contului.</p>
        <p>Poți alege categoriile de alerte opționale din <a href="/notificari?preferinte=1">preferințele de notificare</a>. Alegerea se păstrează în cont și se aplică notificărilor viitoare. Mesajele despre cont, comenzi și moderare rămân active.</p>
        <h2>Stocarea în browser</h2>
        <p>Aplicația păstrează local sesiunea de autentificare, preferințele de afișare și selecția pentru comparație. Deconectarea elimină sesiunea salvată pe dispozitiv și solicită revocarea ei pe server. Dacă revocarea nu poate fi confirmată, aplicația te anunță.</p>
        <h2>Solicitări privind datele</h2>
        <p>Poți cere accesul la date, corectarea informațiilor și ștergerea contului la adresa operatorului. Datele asociate comenzilor în curs necesită finalizarea sau anularea acestora înainte de ștergerea din aplicație.</p>
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
  </article>`;
}
