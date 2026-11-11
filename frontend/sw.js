const CACHE = 'gt-rewardhub-offline-1';
const OFFLINE = '/offline.html';
const STYLES = '/css/app.css';

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll([OFFLINE, STYLES])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(caches.keys().then((names) => Promise.all(names
    .filter((name) => name === 'gt-rewardhub-v2' || (name.startsWith('gt-rewardhub-offline-') && name !== CACHE))
    .map((name) => caches.delete(name)))).then(() => self.clients.claim()));
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);
  if (request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/RewardHub/')) return;
  if (request.mode === 'navigate') {
    event.respondWith(fetch(request).catch(() => caches.open(CACHE).then((cache) => cache.match(OFFLINE))));
  } else if (url.pathname === STYLES) {
    event.respondWith(fetch(request).catch(() => caches.open(CACHE).then((cache) => cache.match(STYLES))));
  }
});
