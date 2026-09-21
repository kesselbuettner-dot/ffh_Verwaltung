package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Set;

/** Admin-only diagnosis of the two coexist­ing role systems. No password or token data. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class PermissionAuditController {
    private final AppUserRepository users;
    private final ManagedUserRoleRepository assignments;
    private final EffectivePermissionService permissions;

    public PermissionAuditController(AppUserRepository users,
                                     ManagedUserRoleRepository assignments,
                                     EffectivePermissionService permissions) {
        this.users = users;
        this.assignments = assignments;
        this.permissions = permissions;
    }

    @GetMapping("/{id}/permission-audit")
    @Transactional(readOnly = true)
    public Audit audit(@PathVariable Long id) {
        AppUser user = users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden."));
        List<String> managed = assignments.findByUserId(id).stream()
                .map(a -> a.getRole().getCode()).distinct().sorted().toList();
        String primary = user.getRole() == null ? "" : user.getRole().name();
        boolean missingPrimary = !managed.contains(primary);
        boolean adminMismatch = managed.contains("ADMIN") != "ADMIN".equals(primary);
        Set<String> effective = permissions.permissionsFor(id);
        return new Audit(user.getUsername(), primary, managed,
                user.isEnabled(), user.isRegistrationApproved(),
                missingPrimary, adminMismatch,
                effective.stream().sorted().toList());
    }

    public record Audit(String username, String primaryRole,
                        List<String> managedRoles, boolean enabled, boolean registrationApproved,
                        boolean missingPrimaryAssignment, boolean administratorMismatch,
                        List<String> effectivePermissions) {}
}
