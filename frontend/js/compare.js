import { emit, pref, setPref } from './store.js';

export function comparedIds(values = pref('compare', [])) {
  if (!Array.isArray(values)) return [];
  return [...new Set(values.filter((id) => typeof id === 'string' && /^[a-zA-Z0-9_-]{1,64}$/.test(id)))].slice(0, 3);
}

export function setCompared(values) {
  const ids = comparedIds(values);
  setPref('compare', ids);
  emit('compare', ids);
  return ids;
}

export function toggleCompared(id) {
  const ids = comparedIds();
  if (ids.includes(id)) return setCompared(ids.filter((value) => value !== id));
  if (ids.length >= 3) return null;
  return setCompared([...ids, id]);
}

export const compareLink = (ids = comparedIds()) => `/compara${ids.length ? `?ids=${ids.map(encodeURIComponent).join(',')}` : ''}`;
