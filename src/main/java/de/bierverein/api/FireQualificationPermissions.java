package de.bierverein.api;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
@Service("fireQualificationPermissions")
public class FireQualificationPermissions {
 private final EffectivePermissionService permissions;
 public FireQualificationPermissions(EffectivePermissionService permissions){this.permissions=permissions;}
 public boolean allowed(Authentication authentication,String permission){
  if(!(authentication instanceof JwtAuthenticationToken jwt))return false;
  Object raw=jwt.getToken().getClaim("userId");
  return raw instanceof Number id && permissions.hasPermission(id.longValue(),permission);
 }
 public Long userId(Authentication authentication){
  if(!(authentication instanceof JwtAuthenticationToken jwt))return null;
  Object raw=jwt.getToken().getClaim("userId");
  return raw instanceof Number id?id.longValue():null;
 }
}
