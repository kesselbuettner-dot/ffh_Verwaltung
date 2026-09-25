package de.bierverein.api;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed effective permissions. Does not trust role claims embedded
 * in an older JWT. Integration with SecurityConfig and controllers is pending.
 */
@Service
public class EffectivePermissionService {
    private final AppUserRepository users;
    private final ManagedUserRoleRepository assignments;
    private final ManagedRolePermissionRepository permissions;

    public EffectivePermissionService(AppUserRepository users,
                                      ManagedUserRoleRepository assignments,
                                      ManagedRolePermissionRepository permissions) {
        this.users = users;
        this.assignments = assignments;
        this.permissions = permissions;
    }

    @Transactional(readOnly = true)
    public Set<String> permissionsFor(Long userId) {
        if (userId == null) return Set.of();
        AppUser user = users.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || !user.isRegistrationApproved()) return Set.of();
        var userRoles = assignments.findByUserId(userId);
        // Keep the protected system ADMIN role as the explicit administrative override.
        if (userRoles.stream().anyMatch(a ->
                a.getRole().isSystemRole() && "ADMIN".equals(a.getRole().getCode()))) {
            return Set.copyOf(PermissionCatalog.keys());
        }
        Set<String> granted = new HashSet<>();
        for (ManagedUserRole assignment : userRoles) {
            for (ManagedRolePermission permission :
                    permissions.findByRoleId(assignment.getRole().getId())) {
                granted.add(permission.getPermissionKey());
            }
        }
        // A write or delete grant implies read, but never the reverse.
        Set<String> effective = new HashSet<>(granted);
        for (String key : granted) {
            if (key.endsWith(".write") || key.endsWith(".delete")) {
                effective.add(key.substring(0, key.lastIndexOf('.')) + ".read");
            }
        }
        effective.retainAll(PermissionCatalog.keys());
        return Set.copyOf(effective);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(Long userId, String permissionKey) {
        return permissionKey != null && PermissionCatalog.keys().contains(permissionKey)
                && permissionsFor(userId).contains(permissionKey);
    }

}
