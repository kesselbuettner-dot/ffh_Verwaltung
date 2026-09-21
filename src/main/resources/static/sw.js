const CACHE_NAME = "ffh-verwaltung-menu-designer-v2";
const APP_SHELL = [
  "/",
  "/manifest.json",
  "/menu-designer.js",
  "/menu-designer.css",
  "/icons/fw-cockpit-logo.svg",
  "/icons/fw-cockpit-192.png",
  "/icons/fw-cockpit-512.png"
];

self.addEventListener("install", event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(APP_SHELL))
  );
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(
        keys
          .filter(key => key !== CACHE_NAME)
          .map(key => caches.delete(key))
      )
    )
  );
  self.clients.claim();
});

self.addEventListener("fetch", event => {
  if (event.request.method !== "GET") return;

  const url = new URL(event.request.url);

  // API responses contain live and potentially user-specific data.
  // Never cache authenticated API requests.
  if (url.pathname.startsWith("/api/")) return;

  // Navigation stays network-first so deployments become visible quickly.
  if (event.request.mode === "navigate" || url.pathname === "/") {
    event.respondWith(
      fetch(event.request)
        .then(response => {
          if (response.ok) {
            const copy = response.clone();
            caches.open(CACHE_NAME).then(cache => cache.put("/", copy));
          }
          return response;
        })
        .catch(() =>
          caches.match(event.request).then(
            cached => cached || caches.match("/")
          )
        )
    );
    return;
  }

  // Static assets: cache-first with network fallback.
  event.respondWith(
    caches.match(event.request).then(cached =>
      cached ||
      fetch(event.request).then(response => {
        if (response.ok) {
          const copy = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put(event.request, copy));
        }
        return response;
      })
    )
  );
});
