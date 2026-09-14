package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.http.HttpStatus; import org.springframework.web.server.ResponseStatusException; import java.util.*;
@RestController @RequestMapping("/api/admin/users") @PreAuthorize("hasRole('ADMIN')") public class UserAdminController {
 private final AppUserRepository users; private final MemberRepository members; private final PasswordEncoder encoder;
 public UserAdminController(AppUserRepository u,MemberRepository m,PasswordEncoder e){users=u;members=m;encoder=e;}
 @GetMapping public List<UserDto> all(){return users.findAll().stream().map(this::dto).toList();}
 @PutMapping("/{id}") public UserDto update(@PathVariable Long id,@RequestBody UserUpdate r){
  AppUser u=users.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Benutzer nicht gefunden"));
  if(r.username()==null||r.username().isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Benutzername ist erforderlich");
  String username=r.username().trim(); users.findByUsername(username).ifPresent(other->{if(!other.getId().equals(id))throw new ResponseStatusException(HttpStatus.CONFLICT,"Benutzername existiert bereits");});
  if(r.memberId()!=null){Member m=members.findById(r.memberId()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Mitglied nicht gefunden")); users.findAll().stream().filter(x->!x.getId().equals(id)&&x.getMember()!=null&&x.getMember().getId().equals(m.getId())).findFirst().ifPresent(x->{throw new ResponseStatusException(HttpStatus.CONFLICT,"Dieses Mitglied hat bereits einen Login");}); u.setMember(m);}
  u.setUsername(username); if(r.role()!=null)u.setRole(r.role()); if(r.enabled()!=null)u.setEnabled(r.enabled()); if(r.password()!=null&&!r.password().isBlank())u.setPasswordHash(encoder.encode(r.password())); return dto(users.save(u));
 }
 private UserDto dto(AppUser u){return new UserDto(u.getId(),u.getUsername(),u.getRole(),u.isEnabled(),u.getMember()==null?null:u.getMember().getId(),u.getMember()==null?null:u.getMember().getName());}
 public record UserDto(Long id,String username,Role role,boolean enabled,Long memberId,String memberName){}
 public record UserUpdate(String username,String password,Role role,Boolean enabled,Long memberId){}
}
