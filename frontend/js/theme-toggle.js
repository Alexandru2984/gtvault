// Comutarea temei; alegerea explicita se pastreaza, altfel urmam setarea sistemului.
import { pref, setPref } from './store.js';

const media = window.matchMedia('(prefers-color-scheme: dark)');

export const currentTheme = () => document.documentElement.getAttribute('data-theme') || 'light';

function apply(theme) {
  document.documentElement.setAttribute('data-theme', theme);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.content = theme === 'dark' ? '#0c0b12' : '#7c3aed';
}

export function toggleTheme() {
  const next = currentTheme() === 'dark' ? 'light' : 'dark';
  setPref('theme', next);
  apply(next);
}

media.addEventListener?.('change', (e) => {
  if (pref('theme') == null) apply(e.matches ? 'dark' : 'light');
});

apply(currentTheme());
