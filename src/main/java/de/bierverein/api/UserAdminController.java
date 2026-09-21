package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.http.HttpStatus; import org.springframework.web.server.ResponseStatusException; import java.util.*; import org.springframework.transaction.annotation.Transactional;
@RestController @RequestMapping("/api/admin/users") @PreAuthorize("hasRole('ADMIN')") public class UserAdminController {
 private final AppUserRepository users; private final MemberRepository members; private final PasswordEncoder encoder; private final PrimaryRoleSyncService primaryRoles; private final ManagedUserRoleRepository managedAssignments;
 public UserAdminController(AppUserRepository u,MemberRepository m,PasswordEncoder e,PrimaryRoleSyncService primaryRoles, ManagedUserRoleRepository managedAssignments){users=u;members=m;encoder=e;this.primaryRoles=primaryRoles;this.managedAssignments=managedAssignments;}
 @GetMapping @Transactional(readOnly = true) public List<UserDto> all(){return users.findAll().stream().map(this::dto).toList();}
 @PostMapping("/{id}/approve") @Transactional public UserDto approve(@PathVariable Long id){ AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Benutzer nicht gefunden")); Role prior=u.getRole(); u.setRegistrationApproved(true); u.setEnabled(true); u.setRole(Role.MEMBER); if(u.getMember()!=null){u.getMember().setActive(true); members.save(u.getMember());} users.save(u); primaryRoles.sync(u,prior); return dto(u); }
 @PutMapping("/{id}") @Transactional public UserDto update(@PathVariable Long id,@RequestBody UserUpdate r){
  AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Benutzer nicht gefunden"));
  if(r.username()==null||r.username().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Benutzername ist erforderlich");
  String username=r.username().trim(); users.findByUsername(username).ifPresent(other->{if(!other.getId().equals(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Benutzername existiert bereits");});
  if(r.memberId()!=null){Member m=members.findById(r.memberId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied nicht gefunden")); users.findAll().stream().filter(x->!x.getId().equals(id)&&x.getMember()!=null&&x.getMember().getId().equals(m.getId())).findFirst().ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Dieses Mitglied hat bereits einen Login");}); u.setMember(m);}
  Role previous=u.getRole();
  boolean removeActiveAdmin = previous==Role.ADMIN && u.isEnabled()
      && (r.role()!=null && r.role()!=Role.ADMIN || Boolean.FALSE.equals(r.enabled()));
  if(removeActiveAdmin && managedAssignments.countByRoleCodeAndUserEnabled("ADMIN",true)<=1)
      throw new ResponseStatusException(HttpStatus.CONFLICT,"Der letzte aktive Administrator darf weder deaktiviert noch herabgestuft werden.");
  u.setUsername(username);
  if(r.role()!=null)u.setRole(r.role());
  if(r.enabled()!=null)u.setEnabled(r.enabled());
  if(Boolean.TRUE.equals(r.enabled()))u.setRegistrationApproved(true);
  if(r.password()!=null&&!r.password().isBlank())u.setPasswordHash(encoder.encode(r.password()));
  users.save(u);
  if(r.role()!=null && r.role()!=previous) primaryRoles.sync(u,previous);
  return dto(u);
 }
 private UserDto dto(AppUser u){return new UserDto(u.getId(),u.getUsername(),u.getRole(),u.isEnabled(),u.isRegistrationApproved(),u.getMember()==null?null:u.getMember().getId(),u.getMember()==null?null:u.getMember().getName(),u.getMember()==null?null:u.getMember().getEmail());}
 public record UserDto(Long id,String username,Role role,boolean enabled,boolean registrationApproved,Long memberId,String memberName,String email){}
 public record UserUpdate(String username,String password,Role role,Boolean enabled,Long memberId){}
}
