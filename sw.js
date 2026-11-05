/*
  RewardHub Service Worker — Dragne Alexandru Mihai
  Offline support, caching of shell assets & instant loading
*/

const CACHE_NAME = 'gt-rewardhub-v2';
const STATIC_ASSETS = [
  '/',
  '/gtshop.html',
  '/styles.css',
  '/app.js',
  '/manifest.json',
  '/images/gt-logo.svg',
  '/images/user-avatar.svg',
  '/images/micus-stuff-logo.png',
  '/images/agenda.svg',
  '/images/chatgpt.svg',
  '/images/cv.svg',
  '/images/lucky.svg',
  '/images/meet.svg',
  '/images/mentorat.svg',
  '/images/mouse.svg',
  '/images/sticker.svg',
  '/images/tracks.svg',
  '/images/tricou.svg',
  '/images/udemy.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      return cache.addAll(STATIC_ASSETS);
    })
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((keys) => {
      return Promise.all(
        keys.map((key) => {
          if (key !== CACHE_NAME) {
            return caches.delete(key);
          }
        })
      );
    })
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // Never cache API calls (auth, checkout, points, cart)
  if (url.pathname.startsWith('/RewardHub/api')) {
    event.respondWith(fetch(event.request));
    return;
  }

  // Network-first for HTML pages so user gets fresh deploy immediately
  if (event.request.mode === 'navigate' || event.request.headers.get('accept')?.includes('text/html')) {
    event.respondWith(
      fetch(event.request).catch(() => caches.match('/gtshop.html') || caches.match('/'))
    );
    return;
  }

  // Cache-first for images, CSS, scripts, fonts
  event.respondWith(
    caches.match(event.request).then((cached) => {
      if (cached) return cached;
      return fetch(event.request).then((response) => {
        if (response && response.status === 200 && response.type === 'basic') {
          const clone = response.clone();
          caches.open(CACHE_NAME).then((cache) => cache.put(event.request, clone));
        }
        return response;
      });
    }).catch(() => {
      if (event.request.destination === 'image') {
        return caches.match('/images/gt-logo.svg');
      }
    })
  );
});
