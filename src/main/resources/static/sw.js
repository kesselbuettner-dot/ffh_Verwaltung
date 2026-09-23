const CACHE_NAME = "ffh-verwaltung-driving-scan-v48";
const APP_SHELL = [
  "/",
  "/manifest.json",
  "/menu-designer.js?v=577e741-v2",
  "/menu-designer.css?v=577e741-v2",
  "/wehrleiter.js?v=7",
  "/wehrleiter.css?v=6",
  "/ui-theme.css?v=2",
  "/design-system.css?v=1",
  "/design-system.js?v=1",
  "/page-templates.css?v=1",
  "/page-templates.js?v=2",
  "/print-templates.js?v=1",
  "/print-templates.css?v=1",
  "/print-reports.js?v=1",
  "/inspection-signature.js?v=1",
  "/device-cycle-tasks.js?v=2",
  "/inspection-management.js?v=2",
  "/icons/fw-cockpit-brand.svg",
  "/icons/fw-cockpit-icon-192.png",
  "/icons/fw-cockpit-icon-512.png",
  "/icons/fw-cockpit-icon-maskable-512.png"
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

self.addEventListener("notificationclick", event => {
  event.notification.close();
  event.waitUntil(
    clients.matchAll({ type: "window", includeUncontrolled: true }).then(windows => {
      const existing = windows.find(client => "focus" in client);
      const target = event.notification.data?.url || "/?messages=1";
      return existing ? existing.focus().then(client => client.navigate(target)) : clients.openWindow(target);
    })
  );
});

self.addEventListener("push", event => {
  let data = { title: "FFH Verwaltung", body: "Eine neue Meldung ist verfügbar.", url: "/?messages=1" };
  try { if (event.data) data = { ...data, ...event.data.json() }; } catch (_) {}
  event.waitUntil(self.registration.showNotification(data.title, {
    body: data.body,
    icon: "/icons/fw-cockpit-brand.svg",
    badge: "/icons/fw-cockpit-brand.svg",
    tag: data.tag || "ffh-message",
    data: { url: data.url || "/?messages=1" }
  }));
});
