import { html, join, mount } from '../dom.js';
import { setMeta } from '../seo.js';

const questions = [
  ['Cum câștig puncte de activitate?', html`Revendică recompensa zilnică, răspunde la quiz și termină misiunile din <a href="/activitati">Activități</a>. Poți juca și <a href="/wordle">Tech Wordle</a>. Soldul și mișcările apar în <a href="/istoric">istoricul de puncte</a>.`],
  ['Cum folosesc punctele?', html`Alege o recompensă din <a href="/magazin">magazin</a> și adaug-o în coș. Verifică totalul înainte de a plasa comanda.`],
  ['Unde găsesc voucherul sau AWB-ul?', html`Deschide comanda din <a href="/comenzi">Comenzile mele</a>. Codul digital apare lângă produs, iar AWB-ul apare după ce echipa îl adaugă la expediere.`],
  ['Cum verific un cod digital?', html`Deschide <a href="/vouchere">verificatorul de vouchere</a> și introdu codul primit. Vezi produsul și dacă voucherul este activ, folosit sau anulat.`],
  ['Pot anula o comandă?', html`Deschide comanda și verifică dacă apare opțiunea de anulare. Pentru o comandă eligibilă, punctele revin în sold după anulare.`],
  ['Cum îmi schimb parola sau descarc datele?', html`Găsești ambele opțiuni în <a href="/profil?tab=setari">setările contului</a>. Tot acolo poți deconecta celelalte sesiuni sau cere ștergerea contului.`],
];

export default function help({ root }) {
  setMeta({ title: 'Întrebări frecvente', description: 'Cum câștigi puncte de activitate, comanzi premii și îți gestionezi contul RewardHub.' });
  mount(root, html`<section class="stack narrow">
    <h1>Întrebări frecvente</h1>
    ${join(questions, ([question, answer]) => html`<details class="card card-pad"><summary class="strong">${question}</summary><p class="muted mt-1">${answer}</p></details>`)}
    <p>Ai nevoie de ajutor cu o comandă? Scrie la <a href="mailto:984.alexmihai@proton.me">984.alexmihai@proton.me</a> și include numărul comenzii.</p>
  </section>`);
}
