import { TZ } from './format.js';

// Campurile datetime-local sunt întotdeauna interpretate în ora României, indiferent de browser.
const clock = new Intl.DateTimeFormat('en-GB', { timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
export function promotionLocalTime(instant) {
  if (!instant) return '';
  const date = new Date(instant);
  if (!Number.isFinite(date.getTime())) return '';
  const p = Object.fromEntries(clock.formatToParts(date).map(({ type, value }) => [type, value]));
  return `${p.year}-${p.month}-${p.day}T${p.hour}:${p.minute}`;
}

export function promotionInstant(wall) {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(wall) || Number(wall.slice(0, 4)) < 1900 || Number(wall.slice(0, 4)) > 9998) throw new Error('Alege o dată și o oră valide.');
  const nominal = Date.parse(wall + ':00Z');
  if (!Number.isFinite(nominal) || new Date(nominal).toISOString().slice(0, 16) !== wall) throw new Error('Alege o dată și o oră valide.');
  const matches = [];
  for (let minutes = -14 * 60; minutes <= 14 * 60; minutes += 15) {
    const instant = new Date(nominal + minutes * 60_000).toISOString();
    if (promotionLocalTime(instant) === wall) matches.push(instant);
  }
  if (matches.length === 0) throw new Error('Ora aleasă nu există în România la trecerea la ora de vară. Alege altă oră.');
  if (matches.length > 1) throw new Error('Ora aleasă apare de două ori în România la trecerea la ora de iarnă. Alege altă oră.');
  return matches[0];
}

export function flashPreview(price, percent) {
  return Math.max(1, Math.floor(price * (100 - percent) / 100));
}

/** Simulare pentru o comandă eligibilă, pe suma rămasă după oferta flash și reducerea de nivel. */
export function promoPreview(amount, { discountPercent, discountPoints, maxDiscount, minOrderPoints }) {
  if (!Number.isSafeInteger(amount) || amount < 0 || amount > 1_980_000_000) return null;
  if (amount < minOrderPoints) return { discount: 0, total: amount, minimum: true };
  let discount = discountPercent > 0 ? Math.floor(amount * discountPercent / 100) : discountPoints;
  if (discountPercent > 0 && maxDiscount > 0) discount = Math.min(discount, maxDiscount);
  discount = Math.max(0, Math.min(amount, discount));
  return { discount, total: amount - discount, minimum: false };
}
