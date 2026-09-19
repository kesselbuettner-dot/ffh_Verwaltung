package de.bierverein.api;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedRoleAdminControllerTest {
    ManagedRoleRepository roles;
    ManagedRolePermissionRepository permissions;
    ManagedUserRoleRepository assignments;
    ManagedRoleAdminController controller;

    @BeforeEach void setup() {
        roles = mock(ManagedRoleRepository.class);
        permissions = mock(ManagedRolePermissionRepository.class);
        assignments = mock(ManagedUserRoleRepository.class);
        controller = new ManagedRoleAdminController(roles, permissions, assignments);
    }

    @Test void rejectsUnknownPermissionBeforeWriting() {
        assertThrows(ResponseStatusException.class, () ->
            controller.create(new ManagedRoleAdminController.RoleInput(
                "TEST", "Test", null, List.of("invalid.permission"))));
        verify(roles, never()).save(any());
    }

    @Test void rejectsDuplicatePermissionBeforeWriting() {
        assertThrows(ResponseStatusException.class, () ->
            controller.create(new ManagedRoleAdminController.RoleInput(
                "TEST", "Test", null, List.of("members.read", "members.read"))));
        verify(roles, never()).save(any());
    }

    @Test void systemRoleCannotBeChangedOrDeleted() {
        ManagedRole admin = new ManagedRole("ADMIN", "Administrator", null, true);
        when(roles.findById(1L)).thenReturn(Optional.of(admin));
        assertThrows(ResponseStatusException.class, () ->
            controller.update(1L, new ManagedRoleAdminController.RoleInput(
                "ADMIN", "Changed", null, List.of())));
        assertThrows(ResponseStatusException.class, () -> controller.delete(1L));
        verify(roles, never()).delete(any());
    }

    @Test void assignedCustomRoleCannotBeDeleted() {
        ManagedRole role = new ManagedRole("CUSTOM", "Custom", null, false);
        when(roles.findById(1L)).thenReturn(Optional.of(role));
        when(assignments.existsByRoleId(1L)).thenReturn(true);
        assertThrows(ResponseStatusException.class, () -> controller.delete(1L));
        verify(roles, never()).delete(any());
    }
}
