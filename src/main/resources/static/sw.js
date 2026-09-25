const CACHE_NAME = "ffh-verwaltung-task-scope-search-20260925";
const APP_SHELL = [
  "/",
  "/manifest.json",
  "/tabler-icons.js?v=menu-icon-fix-20260924",
  "/menu-designer.js?v=parent-notice-badges-20260924",
  "/menu-designer.css?v=icon-only-v2",
  "/wehrleiter.js?v=qualification-a4-report-v4",
  "/wehrleiter.css?v=qualification-a4-report-v4",
  "/ui-theme.css?v=device-dropdowns-v4",
  "/design-system.css?v=1",
  "/design-system.js?v=1",
  "/page-templates.css?v=1",
  "/page-templates.js?v=2",
  "/print-templates.js?v=attendance-print-20260924-v2",
  "/print-templates.css?v=2",
  "/print-reports.js?v=attendance-print-20260924-v2",
  "/inspection-signature.js?v=1",
  "/device-cycle-tasks.js?v=parent-notice-badges-20260924",
  "/my-tasks.js?v=scope-search-20260925",
  "/inspection-management.js?v=2",
  "/icons/menu/helmet.svg",
  "/icons/menu/engine.svg",
  "/icons/menu/extinguisher.svg",
  "/icons/menu/radio.svg",
  "/icons/menu/hose.svg",
  "/icons/menu/ladder.svg",
  "/icons/menu/flame.svg",
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
