package de.bierverein.api;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class PermissionCatalogTest {
    @Test void everyAreaHasUniqueKeyAndKnownParent() {
        var areas = PermissionCatalog.byKey();
        assertEquals(PermissionCatalog.areas().size(), areas.size());
        for (var area : areas.values()) {
            if (area.parentKey() != null) assertTrue(areas.containsKey(area.parentKey()));
        }
    }

    @Test void allAreasHaveDistinctReadWriteDeleteKeys() {
        var keys = PermissionCatalog.keys();
        assertEquals(PermissionCatalog.areas().size() * 3, keys.size());
        assertEquals(keys.size(), new HashSet<>(keys).size());
        assertTrue(keys.contains("fire.devices.read"));
        assertTrue(keys.contains("administration.roles.write"));
    }

    @Test void unknownAreasAreRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> PermissionCatalog.key("unknown", PermissionCatalog.Action.READ));
    }
}
