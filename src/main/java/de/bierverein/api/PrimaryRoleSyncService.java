package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

/**
 * Synchronizes the legacy primary role with the managed system assignment.
 * Deliberately preserves additional custom assignments.
 * Never runs on application startup to avoid restoring permissions revoked by admins.
 */
@Service
public class PrimaryRoleSyncService {
    private final ManagedRoleRepository roles;
    private final ManagedUserRoleRepository assignments;

    public PrimaryRoleSyncService(ManagedRoleRepository roles,
                                  ManagedUserRoleRepository assignments) {
        this.roles = roles;
        this.assignments = assignments;
    }

    @Transactional
    public void sync(AppUser user, Role previousRole) {
        if (user == null || user.getId() == null || user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Benutzer oder Hauptrolle fehlt.");
        }
        ManagedRole primary = roles.findByCode(user.getRole().name()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.CONFLICT, "Systemrolle noch nicht eingerichtet."));
        if (!primary.isSystemRole()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Ungültige Systemrolle.");
        List<ManagedUserRole> current = assignments.findByUserId(user.getId());
        if (previousRole != null && previousRole != user.getRole()) {
            for (ManagedUserRole assignment : current) {
                ManagedRole old = assignment.getRole();
                if (old.isSystemRole() && old.getCode().equals(previousRole.name())) {
                    assignments.delete(assignment);
                }
            }
        }
        if (current.stream().noneMatch(a -> java.util.Objects.equals(a.getRole().getId(), primary.getId()))) {
            assignments.save(new ManagedUserRole(user, primary));
        }
    }
}
