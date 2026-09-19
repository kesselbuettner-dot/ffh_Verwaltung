package de.bierverein.api;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManagedUserRoleAdminControllerTest {
    private AppUserRepository users;
    private ManagedRoleRepository roles;
    private ManagedUserRoleRepository assignments;
    private ManagedUserRoleAdminController controller;
    private AppUser user;

    @BeforeEach void setup() {
        users = mock(AppUserRepository.class);
        roles = mock(ManagedRoleRepository.class);
        assignments = mock(ManagedUserRoleRepository.class);
        controller = new ManagedUserRoleAdminController(users, roles, assignments);
        user = new AppUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
    }

    @Test void unknownRoleDoesNotDeleteExistingAssignments() {
        when(roles.findAllById(any())).thenReturn(List.of());
        assertThrows(ResponseStatusException.class, () ->
            controller.replace(1L, new ManagedUserRoleAdminController.RoleAssignment(List.of(999L))));
        verify(assignments, never()).deleteAll(any());
    }

    @Test void duplicateRoleIdsAreRejectedBeforeDatabaseWrites() {
        assertThrows(ResponseStatusException.class, () ->
            controller.replace(1L, new ManagedUserRoleAdminController.RoleAssignment(List.of(2L, 2L))));
        verify(assignments, never()).deleteAll(any());
    }

    @Test void lastActiveAdministratorCannotLoseAdminRole() {
        ManagedRole admin = new ManagedRole("ADMIN", "Administrator", null, true);
        ManagedRole member = new ManagedRole("MEMBER", "Mitglied", null, true);
        when(roles.findAllById(any())).thenReturn(List.of(member));
        when(assignments.findByUserId(1L)).thenReturn(List.of(new ManagedUserRole(user, admin)));
        when(assignments.countByRoleCodeAndUserEnabled("ADMIN", true)).thenReturn(1L);
        assertThrows(ResponseStatusException.class, () ->
            controller.replace(1L, new ManagedUserRoleAdminController.RoleAssignment(List.of(2L))));
        verify(assignments, never()).deleteAll(any());
    }

    @Test void legacyAdministratorCannotSilentlyRetainAdminAccessAfterRemoval() {
        user.setRole(Role.ADMIN);
        ManagedRole admin = new ManagedRole("ADMIN", "Administrator", null, true);
        ManagedRole member = new ManagedRole("MEMBER", "Mitglied", null, true);
        when(roles.findAllById(any())).thenReturn(List.of(member));
        when(assignments.findByUserId(1L)).thenReturn(List.of(new ManagedUserRole(user, admin)));
        when(assignments.countByRoleCodeAndUserEnabled("ADMIN", true)).thenReturn(2L);
        assertThrows(ResponseStatusException.class, () ->
            controller.replace(1L, new ManagedUserRoleAdminController.RoleAssignment(List.of(2L))));
        verify(assignments, never()).deleteAll(any());
    }
}
