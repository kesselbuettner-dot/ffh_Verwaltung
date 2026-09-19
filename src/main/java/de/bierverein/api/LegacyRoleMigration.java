package de.bierverein.api;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Additive, idempotent initial migration of the legacy AppUser.role column.
 * Legacy login and authorization remain unchanged until the new permissions
 * implementation is complete. No legacy data is deleted or rewritten here.
 */
@Component
public class LegacyRoleMigration {
    private final ManagedRoleRepository roles;
    private final ManagedUserRoleRepository assignments;
    private final AppUserRepository users;

    public LegacyRoleMigration(ManagedRoleRepository roles,
                               ManagedUserRoleRepository assignments,
                               AppUserRepository users) {
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        Map<Role, ManagedRole> legacyRoles = new EnumMap<>(Role.class);
        for (Role legacy : Role.values()) {
            ManagedRole role = roles.findByCode(legacy.name())
                .orElseGet(() -> roles.save(new ManagedRole(
                    legacy.name(), displayName(legacy), "Übernommene Systemrolle", true)));
            legacyRoles.put(legacy, role);
        }

        for (AppUser user : users.findAll()) {
            if (user.getId() == null || user.getRole() == null) {
                throw new IllegalStateException("Legacy user has no ID or role");
            }
            // Existing assignments are never overwritten, so rerunning this
            // migration cannot remove additional manually assigned roles.
            if (assignments.findByUserId(user.getId()).isEmpty()) {
                assignments.save(new ManagedUserRole(user, legacyRoles.get(user.getRole())));
            }
        }
    }

    private static String displayName(Role role) {
        return switch (role) {
            case ADMIN -> "Administrator";
            case VORSTAND -> "Vorstand";
            case KASSENWART -> "Kassenwart";
            case FEUERWEHRWART -> "Feuerwehrwart";
            case GERATEWART -> "Gerätewart";
            case GETRAENKEWART -> "Getränkewart";
            case THEKE -> "Theke";
            case MEMBER -> "Mitglied";
        };
    }
}
