package de.bierverein.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Central registry of permission keys. Register new menu areas here before wiring
 * them to frontend visibility and backend authorization.
 *
 * This catalog alone does NOT grant or enforce permissions.
 */
public final class PermissionCatalog {
    public enum Action { READ, WRITE, DELETE }

    public record Area(String key, String title, String parentKey) {
        public Area {
            if (key == null || !key.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*")) {
                throw new IllegalArgumentException("Invalid area key: " + key);
            }
            if (title == null || title.isBlank()) {
                throw new IllegalArgumentException("Area title is required");
            }
        }
    }

    private static final List<Area> AREAS = List.of(
        new Area("dashboard", "Dashboard", null),
        new Area("members", "Mitglieder", null),
        new Area("sales", "Verkauf", null),
        new Area("sales.drinks", "Getränke", "sales"),
        new Area("sales.food", "Essen", "sales"),
        new Area("sales.theke", "Kasse / Theke", "sales"),
        new Area("sales.stock", "Lager", "sales"),
        new Area("events", "Veranstaltungen", null),
        new Area("fire", "Feuerwehr", null),
        new Area("fire.operations", "Einsätze", "fire"),
        new Area("fire.vehicles", "Fahrzeuge", "fire"),
        new Area("fire.devices", "Geräte", "fire"),
        new Area("training", "Schulung / Dienst", null),
        new Area("training.schedule", "Dienstplan", "training"),
        new Area("training.services", "Dienste", "training"),
        new Area("training.courses", "Schulungen / Lehrgänge", "training"),
        new Area("training.documents", "Unterlagen", "training"),
        new Area("finances", "Finanzen", null),
        new Area("finances.cashbook", "Kassenbuch", "finances"),
        new Area("finances.income", "Einnahmen", "finances"),
        new Area("finances.expenses", "Ausgaben", "finances"),
        new Area("finances.fees", "Beiträge", "finances"),
        new Area("finances.donations", "Spenden", "finances"),
        new Area("documents", "Dokumente", null),
        new Area("calendar", "Kalender", null),
        new Area("administration", "Administration", null),
        new Area("administration.users", "Benutzer", "administration"),
        new Area("administration.roles", "Rollen und Rechte", "administration"),
        new Area("administration.settings", "Einstellungen", "administration"),
        new Area("administration.updates", "Updates", "administration")
    );

    private PermissionCatalog() {}

    public static List<Area> areas() { return AREAS; }

    public static String key(String area, Action action) {
        Objects.requireNonNull(action, "action");
        if (AREAS.stream().noneMatch(a -> a.key().equals(area))) {
            throw new IllegalArgumentException("Unknown area: " + area);
        }
        return area + "." + action.name().toLowerCase(java.util.Locale.ROOT);
    }

    public static List<String> keys() {
        List<String> keys = new ArrayList<>();
        for (Area area : AREAS) {
            for (Action action : Action.values()) {
                keys.add(key(area.key(), action));
            }
        }
        return List.copyOf(keys);
    }

    public static Map<String, Area> byKey() {
        Map<String, Area> areas = new LinkedHashMap<>();
        for (Area area : AREAS) {
            if (areas.putIfAbsent(area.key(), area) != null) {
                throw new IllegalStateException("Duplicate area: " + area.key());
            }
            if (area.parentKey() != null && !areas.containsKey(area.parentKey())) {
                throw new IllegalStateException("Parent must precede child: " + area.key());
            }
        }
        return java.util.Collections.unmodifiableMap(areas);
    }
}
