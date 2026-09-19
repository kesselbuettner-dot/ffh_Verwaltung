package de.bierverein.api;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Administration API for custom roles. Legacy authorization is intentionally
 * retained until the new permission enforcement has been fully integrated.
 */
@RestController
@RequestMapping("/api/admin/managed-roles")
@PreAuthorize("hasRole('ADMIN')")
public class ManagedRoleAdminController {
    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private final ManagedRoleRepository roles;
    private final ManagedRolePermissionRepository permissions;
    private final ManagedUserRoleRepository assignments;

    public ManagedRoleAdminController(ManagedRoleRepository roles,
                                      ManagedRolePermissionRepository permissions,
                                      ManagedUserRoleRepository assignments) {
        this.roles = roles;
        this.permissions = permissions;
        this.assignments = assignments;
    }

    @GetMapping("/catalog")
    public List<PermissionCatalog.Area> catalog() {
        return PermissionCatalog.areas();
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<RoleView> list() {
        return roles.findAll().stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RoleView create(@RequestBody RoleInput input) {
        if (input == null || input.code() == null || !CODE.matcher(input.code()).matches()
                || input.name() == null || input.name().isBlank()
                || input.name().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Rolle");
        }
        String code = input.code().toUpperCase(Locale.ROOT);
        if (roles.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rollenkennung existiert bereits");
        }
        Set<String> keys = validate(input.permissions());
        ManagedRole role = roles.save(new ManagedRole(code, input.name(), input.description(), false));
        for (String key : keys) permissions.save(new ManagedRolePermission(role, key));
        return view(role);
    }

    @PutMapping("/{id}")
    @Transactional
    public RoleView update(@PathVariable Long id, @RequestBody RoleInput input) {
        ManagedRole role = find(id);
        if (role.isSystemRole()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Systemrollen sind geschützt");
        }
        if (input == null || input.name() == null || input.name().isBlank()
                || input.name().length() > 120 || input.code() == null
                || !role.getCode().equals(input.code())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Rollenänderung");
        }
        Set<String> keys = validate(input.permissions());
        role.rename(input.name());
        role.setDescription(input.description());
        permissions.deleteAll(permissions.findByRoleId(id));
        permissions.flush();
        for (String key : keys) permissions.save(new ManagedRolePermission(role, key));
        return view(role);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        ManagedRole role = find(id);
        if (role.isSystemRole()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Systemrollen sind geschützt");
        }
        if (assignments.existsByRoleId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Rolle ist Benutzern zugewiesen");
        }
        permissions.deleteAll(permissions.findByRoleId(id));
        permissions.flush();
        roles.delete(role);
    }

    private ManagedRole find(Long id) {
        return roles.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Rolle nicht gefunden"));
    }

    private Set<String> validate(List<String> requested) {
        if (requested == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Berechtigungen fehlen");
        }
        Set<String> keys = new LinkedHashSet<>(requested);
        if (keys.size() != requested.size() || !PermissionCatalog.keys().containsAll(keys)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültige Berechtigungen");
        }
        return keys;
    }

    private RoleView view(ManagedRole role) {
        return new RoleView(role.getId(), role.getCode(), role.getName(),
            role.getDescription(), role.isSystemRole(),
            permissions.findByRoleId(role.getId()).stream()
                .map(ManagedRolePermission::getPermissionKey).sorted().toList());
    }

    public record RoleInput(String code, String name, String description,
                            List<String> permissions) {}
    public record RoleView(Long id, String code, String name, String description,
                           boolean systemRole, List<String> permissions) {}
}
