package de.bierverein.api;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyRoleMigrationTest {
    private ManagedRoleRepository roles;
    private ManagedUserRoleRepository assignments;
    private AppUserRepository users;
    private ManagedRolePermissionRepository permissions;
    private LegacyRoleMigration migration;

    @BeforeEach void setup() {
        roles = mock(ManagedRoleRepository.class);
        assignments = mock(ManagedUserRoleRepository.class);
        users = mock(AppUserRepository.class);
        permissions = mock(ManagedRolePermissionRepository.class);
        migration = new LegacyRoleMigration(roles, assignments, users, permissions);
    }

    @Test void subsequentStartupDoesNotRejectNewUsersWithoutAssignments() {
        when(roles.count()).thenReturn((long) Role.values().length);
        for (Role role : Role.values()) {
            when(roles.findByCode(role.name()))
                .thenReturn(Optional.of(new ManagedRole(role.name(), role.name(), null, true)));
        }
        // Newly registered users can legitimately have no managed roles yet.
        migration.migrate();
        verifyNoInteractions(users, assignments);
    }

    @Test void incompleteSystemRoleCatalogStillFailsClosed() {
        when(roles.count()).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> migration.migrate());
        verifyNoInteractions(users, assignments);
    }
}
