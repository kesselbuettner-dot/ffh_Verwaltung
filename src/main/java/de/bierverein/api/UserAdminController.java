package de.ffh_verwaltung.api;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.*;
import java.util.List;

@RestController @RequestMapping("/api/admin/users")
public class UserAdminController {
 private final AppUserRepository users; private final MemberRepository members; private final PasswordEncoder encoder;
 public UserAdminController(AppUserRepository u,MemberRepository m,PasswordEncoder e){users=u;members=m;encoder=e;}

 @GetMapping public List<AppUser> all(){return users.findAll();}
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public AppUser create(@RequestBody CreateUser req){
   if(users.existsByUsername(req.username())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Benutzer existiert bereits");
   AppUser u=new AppUser(); u.setUsername(req.username()); u.setPasswordHash(encoder.encode(req.password())); u.setRole(req.role());
   if(req.memberId()!=null) u.setMember(members.findById(req.memberId()).orElseThrow());
   return users.save(u);
 }
 public record CreateUser(String username,String password,Role role,Long memberId){}
}
