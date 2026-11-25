// Service worker StarGames: приложение открывается и играется без сети.
// Статика — «сначала кэш, потом обновление в фоне»; /api и внешние адреса — только сеть.
const VERSION = 'sg-v2';
const SHELL = [
  '/', '/index.html', '/style.css', '/app.js', '/manifest.webmanifest',
  '/games/2048.js', '/games/snake.js', '/games/ttt.js', '/games/memory.js', '/games/mines.js', '/games/reaction.js',
  '/icons/icon.svg', '/icons/icon-192.png', '/icons/icon-512.png', '/api/games',
];

self.addEventListener('install', (e) => {
  e.waitUntil(caches.open(VERSION).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== VERSION).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.origin !== location.origin) return;
  // Каталог игр: сначала сеть, без сети — сохранённая копия (чтобы играть офлайн)
  if (url.pathname === '/api/games') {
    e.respondWith(
      caches.open(VERSION).then((cache) =>
        fetch(e.request).then((res) => { if (res.ok) cache.put('/api/games', res.clone()); return res; })
          .catch(() => cache.match('/api/games'))),
    );
    return;
  }
  if (url.pathname.startsWith('/api/')) return;
  const key = e.request.mode === 'navigate' ? '/index.html' : url.pathname;
  e.respondWith(
    caches.open(VERSION).then(async (cache) => {
      const cached = await cache.match(key);
      const fresh = fetch(e.request)
        .then((res) => { if (res.ok) cache.put(key, res.clone()); return res; })
        .catch(() => cached);
      return cached || fresh;
    }),
  );
});
