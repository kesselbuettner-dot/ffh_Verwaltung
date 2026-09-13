package de.ffh_verwaltung.api;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;

@RestController @RequestMapping("/api/auth")
public class AuthController {
 private final AppUserRepository users; private final MemberRepository members; private final PasswordEncoder encoder; private final JwtService jwt;
 public AuthController(AppUserRepository u,MemberRepository m,PasswordEncoder e,JwtService j){users=u;members=m;encoder=e;jwt=j;}

 @PostConstruct
 void seedAdmin(){
   if(!users.existsByUsername("admin")){
     AppUser u=new AppUser(); u.setUsername("admin"); u.setPasswordHash(encoder.encode("admin123!")); u.setRole(Role.ADMIN); users.save(u);
   }
 }

 @PostMapping("/login")
 public LoginResponse login(@RequestBody LoginRequest req){
   AppUser u=users.findByUsername(req.username()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Ungültige Zugangsdaten"));
   if(!u.isEnabled() || !encoder.matches(req.password(),u.getPasswordHash()))
     throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Ungültige Zugangsdaten");
   return new LoginResponse(jwt.create(u),u.getUsername(),u.getRole().name(),u.getMember()==null?null:u.getMember().getId());
 }
 public record LoginRequest(String username,String password){}
 public record LoginResponse(String token,String username,String role,Long memberId){}
}
