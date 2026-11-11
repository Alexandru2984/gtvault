// Client HTTP pentru API-ul RewardHub. Erorile au forma {success:false, error:COD, message}.

export const API_BASE = '/RewardHub/api';
const TIMEOUT_MS = 20000;

export class ApiError extends Error {
  constructor(status, code, message, body = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.body = body;
  }
}

let tokenProvider = () => null;
let onUnauthorized = () => {};
const cartWrites = new Map();

export function configureApi({ getToken, unauthorized }) {
  tokenProvider = getToken;
  onUnauthorized = unauthorized;
}

function withTimeout(signal) {
  const timeout = AbortSignal.timeout ? AbortSignal.timeout(TIMEOUT_MS) : null;
  if (!signal) return timeout;
  if (!timeout) return signal;
  return AbortSignal.any ? AbortSignal.any([signal, timeout]) : signal;
}

const FALLBACK = {
  0: 'Nu mă pot conecta la server. Verifică conexiunea la internet.',
  403: 'Nu ai acces la această acțiune.',
  404: 'Resursa nu a fost găsită.',
  429: 'Prea multe cereri. Mai încearcă peste câteva momente.',
  500: 'A apărut o eroare pe server. Încearcă din nou.',
  502: 'Serverul nu răspunde momentan. Încearcă din nou în câteva momente.',
  503: 'Serviciul este temporar indisponibil.',
};

/**
 * Cerere catre API. Cererile de modificare nu sunt reincercate automat (nu dublam comenzi sau plati);
 * pentru checkout se trimite Idempotency-Key.
 */
export function api(path, options = {}) {
  const token = options.auth === false ? null : tokenProvider();
  const method = options.method || 'GET';
  const pending = path === '/cart' || path === '/checkout/quote'
    ? [...cartWrites].filter(([, owner]) => owner === token).map(([request]) => request) : [];
  const request = sendRequest(path, { ...options, method }, token, pending);
  if (/^\/cart(?:\/|$)/.test(path) && !['GET', 'HEAD'].includes(method)) {
    cartWrites.set(request, token);
    request.then(() => cartWrites.delete(request), () => cartWrites.delete(request));
  }
  return request;
}

async function sendRequest(path, { method, body, signal, idempotencyKey }, token, pending) {
  await Promise.allSettled(pending);
  const headers = { Accept: 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;

  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: withTimeout(signal),
      credentials: 'omit',
      cache: 'no-store',
    });
  } catch (err) {
    if (signal?.aborted) throw err;
    if (err?.name === 'TimeoutError') throw new ApiError(0, 'TIMEOUT', 'Serverul răspunde greu. Încearcă din nou.');
    throw new ApiError(0, 'NETWORK', FALLBACK[0]);
  }

  const type = res.headers.get('content-type') || '';
  let data = null;
  if (res.status !== 204) {
    if (type.includes('application/json')) {
      try { data = await res.json(); } catch { data = null; }
    } else if (!res.ok) {
      await res.text().catch(() => '');
    } else {
      data = await res.blob();
    }
  }

  if (res.ok) return data;

  const code = data?.error || `HTTP_${res.status}`;
  const message = data?.message || FALLBACK[res.status] || (res.status >= 500 ? FALLBACK[500] : 'Cererea nu a putut fi procesată.');
  if (res.status === 401 && token && tokenProvider() === token) onUnauthorized();
  throw new ApiError(res.status, code, message, data);
}

export const get = (path, opts) => api(path, { ...opts, method: 'GET' });
export const post = (path, body, opts) => api(path, { ...opts, method: 'POST', body: body ?? {} });
export const put = (path, body, opts) => api(path, { ...opts, method: 'PUT', body: body ?? {} });
export const del = (path, opts) => api(path, { ...opts, method: 'DELETE' });

/** Construieste un query string ignorand valorile goale. */
export function query(params) {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params || {})) {
    if (v !== undefined && v !== null && v !== '' && v !== false) sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

export const enc = encodeURIComponent;

export function newIdempotencyKey() {
  if (crypto.randomUUID) return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
}

export const isAbort = (err) => err?.name === 'AbortError';
