package de.bierverein.api;
import java.util.List;
import java.util.Set;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin/wehrleiter")
@PreAuthorize("hasRole('ADMIN')")
public class WehrleiterSetupController {
 private final ManagedRoleRepository roles;
 private final ManagedRolePermissionRepository grants;
 public WehrleiterSetupController(ManagedRoleRepository roles,ManagedRolePermissionRepository grants){
  this.roles=roles;this.grants=grants;
 }
 /** Explicit admin action. A second call never overwrites revoked grants. */
 @PostMapping("/create-role") @Transactional
 public Result createRole(){
  var prior=roles.findByCode("WEHRLEITER");
  if(prior.isPresent())return new Result(prior.get().getId(),false,"Rolle ist bereits vorhanden; vorhandene Berechtigungen wurden nicht verändert.");
  ManagedRole role=roles.save(new ManagedRole("WEHRLEITER","Wehrleiter",
    "Qualifikations- und Führerscheinkontrollverwaltung",false));
  for(String permission:List.of("fire.qualifications.read","fire.qualifications.write",
    "fire.qualifications.sensitive.read","fire.drivingcheck.read","fire.drivingcheck.write"))
   grants.save(new ManagedRolePermission(role,permission));
  return new Result(role.getId(),true,"Wehrleiterrolle erstellt. Rechte können im Rollenbereich angepasst werden.");
 }
 public record Result(Long roleId,boolean created,String message){}
}
