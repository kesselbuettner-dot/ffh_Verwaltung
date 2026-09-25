package de.bierverein.api;
import org.springframework.security.core.Authentication;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;import org.springframework.stereotype.Component;
@Component("trainingPermissionGuard")
public class TrainingPermissionGuard {
 private final EffectivePermissionService permissions;public TrainingPermissionGuard(EffectivePermissionService p){permissions=p;}
 public boolean allowed(Authentication a,String area,String action){if(!(a instanceof JwtAuthenticationToken jwt)||!java.util.Set.of("training.services","training.courses","training.documents").contains(area)||!java.util.Set.of("read","write","delete").contains(action))return false;Object id=jwt.getToken().getClaim("userId");return id instanceof Number n&&permissions.hasPermission(n.longValue(),area+"."+action);}
}
