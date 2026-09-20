package de.bierverein.api;
import org.springframework.security.core.Authentication;import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/permissions")
public class CurrentPermissionController {
 private final EffectivePermissionService permissions;public CurrentPermissionController(EffectivePermissionService p){permissions=p;}
 @GetMapping("/me") public Set<String> mine(Authentication a){if(!(a instanceof JwtAuthenticationToken jwt))return Set.of();Object claim=jwt.getToken().getClaim("userId");if(!(claim instanceof Number n))return Set.of();Set<String> result=new LinkedHashSet<>();for(String key:PermissionCatalog.keys())if(permissions.hasPermission(n.longValue(),key))result.add(key);return result;}
}
