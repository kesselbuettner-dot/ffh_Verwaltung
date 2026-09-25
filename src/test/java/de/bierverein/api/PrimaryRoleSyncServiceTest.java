package de.bierverein.api;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrimaryRoleSyncServiceTest {
    @Test
    void newMemberGetsPrimaryAssignment() {
        ManagedRoleRepository roles = mock(ManagedRoleRepository.class);
        ManagedUserRoleRepository assignments = mock(ManagedUserRoleRepository.class);
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setRole(Role.MEMBER);
        ManagedRole member = new ManagedRole("MEMBER", "Mitglied", null, true);
        ReflectionTestUtils.setField(member, "id", 8L);
        when(roles.findByCode("MEMBER")).thenReturn(Optional.of(member));
        when(assignments.findByUserId(7L)).thenReturn(List.of());

        new PrimaryRoleSyncService(roles, assignments).sync(user, null);

        verify(assignments).save(argThat(a -> a.getRole() == member && a.getUser() == user));
    }

    @Test
    void switchingPrimaryRolePreservesExtraCustomRoles() {
        ManagedRoleRepository roles = mock(ManagedRoleRepository.class);
        ManagedUserRoleRepository assignments = mock(ManagedUserRoleRepository.class);
        AppUser user = new AppUser();
        ReflectionTestUtils.setField(user, "id", 7L);
        user.setRole(Role.GERATEWART);
        ManagedRole oldRole = new ManagedRole("MEMBER", "Mitglied", null, true);
        ManagedRole newRole = new ManagedRole("GERATEWART", "Gerätewart", null, true);
        ManagedRole custom = new ManagedRole("WEHRLEITUNG", "Wehrleitung", null, false);
        ReflectionTestUtils.setField(oldRole, "id", 1L);
        ReflectionTestUtils.setField(newRole, "id", 2L);
        ReflectionTestUtils.setField(custom, "id", 3L);
        ManagedUserRole oldAssignment = new ManagedUserRole(user, oldRole);
        ManagedUserRole customAssignment = new ManagedUserRole(user, custom);
        when(roles.findByCode("GERATEWART")).thenReturn(Optional.of(newRole));
        when(assignments.findByUserId(7L)).thenReturn(List.of(oldAssignment, customAssignment));

        new PrimaryRoleSyncService(roles, assignments).sync(user, Role.MEMBER);

        verify(assignments).delete(oldAssignment);
        verify(assignments, never()).delete(customAssignment);
        verify(assignments).save(argThat(a -> a.getRole() == newRole));
    }
}
