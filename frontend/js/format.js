// Formatare pentru limba romana. Module pure (fara DOM), testate cu node --test.

export const TZ = 'Europe/Bucharest';

const nf = new Intl.NumberFormat('ro-RO');
const dateFmt = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short', year: 'numeric', timeZone: TZ });
const shortDateFmt = new Intl.DateTimeFormat('ro-RO', { day: 'numeric', month: 'short', timeZone: TZ });
const dateTimeFmt = new Intl.DateTimeFormat('ro-RO', {
  day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', timeZone: TZ,
});
const timeFmt = new Intl.DateTimeFormat('ro-RO', { hour: '2-digit', minute: '2-digit', timeZone: TZ });

export function num(n) {
  const v = Number(n);
  return nf.format(Number.isFinite(v) ? v : 0);
}

/** "1.250 AP" */
export function ap(n) {
  return `${num(n)} AP`;
}

/** Semn explicit pentru miscari de sold: "+50" / "−20". */
export function signed(n) {
  const v = Number(n) || 0;
  return (v > 0 ? '+' : v < 0 ? '−' : '') + num(Math.abs(v));
}

function toDate(value) {
  if (value == null || value === '') return null;
  const d = value instanceof Date ? value : new Date(value);
  return Number.isNaN(d.getTime()) ? null : d;
}

export function date(value) {
  const d = toDate(value);
  return d ? dateFmt.format(d) : '';
}

export function shortDate(value) {
  const d = toDate(value);
  return d ? shortDateFmt.format(d) : '';
}

export function dateTime(value) {
  const d = toDate(value);
  return d ? dateTimeFmt.format(d) : '';
}

export function time(value) {
  const d = toDate(value);
  return d ? timeFmt.format(d) : '';
}

/**
 * Pluralul romanesc: 1 produs, 2–19 produse, 20+ "de produse" (inclusiv 101–119 fara "de").
 * plural(1, 'produs', 'produse') -> "1 produs"; plural(25, ...) -> "25 de produse".
 */
export function plural(n, one, many) {
  const v = Math.abs(Math.trunc(Number(n) || 0));
  if (v === 1) return `${num(n)} ${one}`;
  const rest = v % 100;
  if (v === 0 || (rest >= 1 && rest <= 19)) return `${num(n)} ${many}`;
  return `${num(n)} de ${many}`;
}

/** "acum 5 minute", "ieri", "acum 3 zile", altfel data calendaristica. */
export function timeAgo(value, now = Date.now()) {
  const d = toDate(value);
  if (!d) return '';
  const diff = Math.round((now - d.getTime()) / 1000);
  if (diff < 0) return dateTime(d);
  if (diff < 45) return 'chiar acum';
  if (diff < 90) return 'acum un minut';
  const min = Math.round(diff / 60);
  if (min < 60) return `acum ${plural(min, 'minut', 'minute')}`;
  const h = Math.round(min / 60);
  if (h < 24) return h === 1 ? 'acum o oră' : `acum ${plural(h, 'oră', 'ore')}`;
  const days = Math.round(h / 24);
  if (days === 1) return 'ieri';
  if (days < 7) return `acum ${plural(days, 'zi', 'zile')}`;
  return date(d);
}

const pad = (n) => String(n).padStart(2, '0');

/** Durata ramasa: "2z 04:05:06" sau "04:05:06"; 0 -> "00:00:00". */
export function countdown(ms) {
  let s = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(s / 86400);
  s -= days * 86400;
  const h = Math.floor(s / 3600);
  s -= h * 3600;
  const m = Math.floor(s / 60);
  s -= m * 60;
  const clock = `${pad(h)}:${pad(m)}:${pad(s)}`;
  return days > 0 ? `${days}z ${clock}` : clock;
}

export function initials(name) {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return '?';
  return (parts[0][0] + (parts.length > 1 ? parts[parts.length - 1][0] : '')).toUpperCase();
}

export function percent(part, total) {
  const t = Number(total) || 0;
  if (t <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round(((Number(part) || 0) / t) * 100)));
}

/** Rating cu o zecimala, virgula romaneasca: 4,5 */
export function rating(value) {
  if (value == null) return '–';
  return new Intl.NumberFormat('ro-RO', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value);
}
