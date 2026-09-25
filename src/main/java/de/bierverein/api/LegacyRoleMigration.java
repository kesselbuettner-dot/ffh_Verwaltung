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
    private final ManagedRolePermissionRepository permissions;

    public LegacyRoleMigration(ManagedRoleRepository roles,
                               ManagedUserRoleRepository assignments,
                               AppUserRepository users,
                               ManagedRolePermissionRepository permissions) {
        this.roles = roles;
        this.assignments = assignments;
        this.users = users;
        this.permissions = permissions;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrate() {
        // One-time bootstrap only: never recreate assignments that an admin
        // deliberately removed after migration. Subsequent user creation and
        // role changes must be handled by the new role-management service.
        // A partially initialized database must never be treated as migrated.
        // Fail closed instead of silently leaving users without assignments.
        long existingRoles = roles.count();
        if (existingRoles != 0L) {
            for (Role legacy : Role.values()) {
                if (roles.findByCode(legacy.name()).isEmpty()) {
                    throw new IllegalStateException("Incomplete legacy role migration: " + legacy);
                }
            }
            // Existing roles may already have been edited by an administrator.
            // Never reseed permissions on restart: doing so would undo deliberate
            // revocations (especially for GERATEWART and training-read grants).
            // Do not infer an incomplete migration from users created later or
            // assignments intentionally removed by an administrator.
            return;
        }
        Map<Role, ManagedRole> legacyRoles = new EnumMap<>(Role.class);
        for (Role legacy : Role.values()) {
            ManagedRole role = roles.findByCode(legacy.name())
                .orElseGet(() -> roles.save(new ManagedRole(
                    legacy.name(), displayName(legacy), "Übernommene Systemrolle", true)));
            legacyRoles.put(legacy, role);
        }

        seedLegacyDevicePermissions();
        seedLegacyTrainingReadPermissions();

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

    /** Preserve legacy ADMIN/GERATEWART device access during the rollout.
     * Only system roles are seeded; custom roles remain fully administrator-controlled. */
    private void seedLegacyDevicePermissions() {
        for (String code : java.util.List.of("ADMIN", "GERATEWART")) {
            ManagedRole role = roles.findByCode(code).orElseThrow(() ->
                new IllegalStateException("Missing legacy role: " + code));
            if (!role.isSystemRole()) {
                throw new IllegalStateException("Legacy role is not protected: " + code);
            }
            java.util.Set<String> existing = permissions.findByRoleId(role.getId()).stream()
                .map(ManagedRolePermission::getPermissionKey)
                .collect(java.util.stream.Collectors.toSet());
            for (String area : java.util.List.of("fire.devices", "fire.vehicles")) {
                for (String action : java.util.List.of("read", "write", "delete")) {
                    String key = area + "." + action;
                    if (!existing.contains(key)) permissions.save(new ManagedRolePermission(role, key));
                }
            }
        }
    }

    /** Keeps the former authenticated read access while write/delete remain configurable. */
    private void seedLegacyTrainingReadPermissions() {
        for (Role legacy : Role.values()) {
            ManagedRole role = roles.findByCode(legacy.name()).orElseThrow();
            java.util.Set<String> existing = permissions.findByRoleId(role.getId()).stream()
                .map(ManagedRolePermission::getPermissionKey).collect(java.util.stream.Collectors.toSet());
            for (String area : java.util.List.of("training.services", "training.courses", "training.documents")) {
                String key = area + ".read";
                if (!existing.contains(key)) permissions.save(new ManagedRolePermission(role, key));
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
