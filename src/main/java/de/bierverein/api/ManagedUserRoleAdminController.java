package de.bierverein.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Replaces a user's managed-role assignments atomically; the legacy role
 * remains untouched until all old endpoints have been migrated. */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class ManagedUserRoleAdminController {
    private final AppUserRepository users;
    private final ManagedRoleRepository roles;
    private final ManagedUserRoleRepository assignments;

    public ManagedUserRoleAdminController(AppUserRepository users,
            ManagedRoleRepository roles, ManagedUserRoleRepository assignments) {
        this.users = users;
        this.roles = roles;
        this.assignments = assignments;
    }

    @GetMapping("/{id}/managed-roles")
    @Transactional(readOnly = true)
    public List<Long> list(@PathVariable Long id) {
        requireUser(id);
        return assignments.findByUserId(id).stream()
            .map(a -> a.getRole().getId()).toList();
    }

    @PutMapping("/{id}/managed-roles")
    @Transactional
    public List<Long> replace(@PathVariable Long id, @RequestBody RoleAssignment request) {
        AppUser user = requireUser(id);
        if (request == null || request.roleIds() == null || request.roleIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mindestens eine Rolle erforderlich");
        }
        Set<Long> ids = new LinkedHashSet<>(request.roleIds());
        if (ids.contains(null) || ids.size() != request.roleIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige oder doppelte Rollen");
        }
        List<ManagedRole> selected = roles.findAllById(ids);
        if (selected.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unbekannte Rolle");
        }
        List<ManagedUserRole> previous = assignments.findByUserId(id);
        boolean wasAdmin = previous.stream().anyMatch(a -> isAdmin(a.getRole()));
        boolean willBeAdmin = selected.stream().anyMatch(ManagedUserRoleAdminController::isAdmin);
        if (wasAdmin && !willBeAdmin && assignments.countByRoleCodeAndUserEnabled("ADMIN", true) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Letzter aktiver Administrator darf nicht entfernt werden");
        }
        // Legacy JWT and @PreAuthorize still read AppUser.role. Do not allow
        // removing a managed ADMIN assignment while the legacy ADMIN claim
        // would continue granting administrator access.
        if (wasAdmin && !willBeAdmin && user.getRole() == Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Administratorrolle kann erst nach Umstellung der alten Autorisierung entfernt werden");
        }
        assignments.deleteAll(previous);
        assignments.flush();
        for (ManagedRole role : selected) {
            assignments.save(new ManagedUserRole(user, role));
        }
        return selected.stream().map(ManagedRole::getId).toList();
    }

    private static boolean isAdmin(ManagedRole role) {
        return role.isSystemRole() && "ADMIN".equals(role.getCode());
    }

    private AppUser requireUser(Long id) {
        return users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Benutzer nicht gefunden"));
    }

    public record RoleAssignment(List<Long> roleIds) {}
}
