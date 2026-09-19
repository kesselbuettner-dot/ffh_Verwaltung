package de.bierverein.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EffectivePermissionServiceTest {
    private AppUserRepository users;
    private ManagedUserRoleRepository assignments;
    private ManagedRolePermissionRepository permissions;
    private EffectivePermissionService service;
    private AppUser user;

    @BeforeEach void setup() {
        users = mock(AppUserRepository.class);
        assignments = mock(ManagedUserRoleRepository.class);
        permissions = mock(ManagedRolePermissionRepository.class);
        service = new EffectivePermissionService(users, assignments, permissions);
        user = new AppUser();
        user.setEnabled(true);
        user.setRegistrationApproved(true);
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test void unknownPermissionAndMissingUserFailClosed() {
        assertFalse(service.hasPermission(1L, "not.registered.read"));
        assertFalse(service.hasPermission(99L, "members.read"));
        assertFalse(service.hasPermission(null, "members.read"));
    }

    @Test void disabledAndUnapprovedUsersHaveNoPermissions() {
        user.setEnabled(false);
        assertFalse(service.hasPermission(1L, "members.read"));
        user.setEnabled(true);
        user.setRegistrationApproved(false);
        assertFalse(service.hasPermission(1L, "members.read"));
    }

    @Test void permissionsAreCombinedAcrossRolesWithoutImplicitWriteAccess() {
        ManagedRole reader = new ManagedRole("READER", "Leser", null, false);
        ManagedRole deviceWriter = new ManagedRole("DEVICE_WRITER", "Geräte", null, false);
        when(assignments.findByUserId(1L)).thenReturn(List.of(
            new ManagedUserRole(user, reader),
            new ManagedUserRole(user, deviceWriter)));
        // Unsaved entities have no ID; repositories can still be stubbed on null
        // for this isolated unit test.
        when(permissions.findByRoleId(null)).thenReturn(List.of(
            new ManagedRolePermission(reader, "members.read"),
            new ManagedRolePermission(deviceWriter, "fire.devices.write")));
        assertTrue(service.hasPermission(1L, "members.read"));
        assertTrue(service.hasPermission(1L, "fire.devices.write"));
        assertFalse(service.hasPermission(1L, "members.write"));
    }

    @Test void protectedAdminAssignmentGrantsKnownPermissions() {
        ManagedRole admin = new ManagedRole("ADMIN", "Administrator", null, true);
        when(assignments.findByUserId(1L)).thenReturn(List.of(new ManagedUserRole(user, admin)));
        assertTrue(service.hasPermission(1L, "administration.roles.write"));
    }

    @Test void customRoleNamedAdminCannotEscalate() {
        ManagedRole fake = new ManagedRole("ADMIN", "Fake", null, false);
        when(assignments.findByUserId(1L)).thenReturn(List.of(new ManagedUserRole(user, fake)));
        assertFalse(service.hasPermission(1L, "administration.roles.write"));
    }
}
