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
    public boolean hasPermission(Long userId, String permissionKey) {
        if (userId == null || permissionKey == null ||
                !PermissionCatalog.keys().contains(permissionKey)) {
            return false;
        }
        AppUser user = users.findById(userId).orElse(null);
        if (user == null || !user.isEnabled() || !user.isRegistrationApproved()) {
            return false;
        }
        var userRoles = assignments.findByUserId(userId);
        // ADMIN is a protected system role. Its assignment is read from the
        // database, never from a stale JWT or the legacy AppUser.role field.
        if (userRoles.stream().anyMatch(a ->
                a.getRole().isSystemRole() && "ADMIN".equals(a.getRole().getCode()))) {
            return true;
        }
        Set<String> granted = new HashSet<>();
        for (ManagedUserRole assignment : userRoles) {
            for (ManagedRolePermission permission :
                    permissions.findByRoleId(assignment.getRole().getId())) {
                granted.add(permission.getPermissionKey());
            }
        }
        return granted.contains(permissionKey);
    }
}
