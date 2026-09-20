package de.bierverein.api;
import org.springframework.security.core.Authentication;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;import org.springframework.stereotype.Component;
@Component("vehiclePermissionGuard")
public class VehiclePermissionGuard {
 private final EffectivePermissionService permissions;public VehiclePermissionGuard(EffectivePermissionService p){permissions=p;}
 public boolean allowed(Authentication a,String action){if(!(a instanceof JwtAuthenticationToken jwt)||!java.util.Set.of("read","write","delete").contains(action))return false;Object id=jwt.getToken().getClaim("userId");return id instanceof Number n&&permissions.hasPermission(n.longValue(),"fire.vehicles."+action);}
}
