package de.bierverein.api;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Resolves the authenticated user from the signed JWT and checks live DB grants. */
@Component("devicePermissionGuard")
public class DevicePermissionGuard {
    private final EffectivePermissionService permissions;

    public DevicePermissionGuard(EffectivePermissionService permissions) {
        this.permissions = permissions;
    }

    public boolean allowed(Authentication authentication, String action) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)
                || !java.util.Set.of("read", "write", "delete").contains(action)) {
            return false;
        }
        Object claim = jwt.getToken().getClaim("userId");
        if (!(claim instanceof Number number)) {
            return false;
        }
        return permissions.hasPermission(number.longValue(), "fire.devices." + action);
    }
}
