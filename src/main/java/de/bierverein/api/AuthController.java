package de.bierverein.api;
import org.springframework.http.*; import org.springframework.transaction.annotation.Transactional; import java.util.Locale; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.web.bind.annotation.*; import jakarta.annotation.PostConstruct; import org.springframework.web.server.ResponseStatusException;
@RestController @RequestMapping("/api/auth") public class AuthController { private final AppUserRepository users; private final MemberRepository members; private final PasswordEncoder encoder; private final JwtService jwt; public AuthController(AppUserRepository u,MemberRepository m,PasswordEncoder e,JwtService j){users=u;members=m;encoder=e;jwt=j;}
 @PostConstruct void seedAdmin(){if(!users.existsByUsername("admin")){AppUser u=new AppUser();u.setUsername("admin");u.setPasswordHash(encoder.encode("admin123!"));u.setRole(Role.ADMIN);users.save(u);}}
 @PostMapping("/login") public LoginResponse login(@RequestBody LoginRequest req){AppUser u=users.findByUsernameIgnoreCase(req.username()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Ungültige Zugangsdaten"));if(!u.isRegistrationApproved())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Deine Registrierung wurde noch nicht durch den Administrator freigeschaltet.");if(!u.isEnabled()||!encoder.matches(req.password(),u.getPasswordHash()))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Ungültige Zugangsdaten");return new LoginResponse(jwt.create(u),u.getUsername(),u.getRole().name(),u.getMember()==null?null:u.getMember().getId(),u.getMember()==null?null:u.getMember().getName());}

 @PostMapping("/register")
 @Transactional
 public RegistrationResponse register(@RequestBody RegistrationRequest req){
   String first=trim(req.firstName()), last=trim(req.lastName()), email=normalizeEmail(req.email());
   if(first==null||last==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Vorname und Nachname sind erforderlich");
   if(email==null||!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bitte eine gültige E-Mail-Adresse eingeben");
   if(req.password()==null||req.password().length()<8) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Das Passwort muss mindestens 8 Zeichen lang sein");
   if(users.findByUsernameIgnoreCase(email).isPresent()||users.findByUsername(email).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Für diese E-Mail-Adresse existiert bereits ein Login");
   if(users.findAll().stream().anyMatch(x->x.getMember()!=null&&x.getMember().getEmail()!=null&&x.getMember().getEmail().equalsIgnoreCase(email))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Diese E-Mail-Adresse ist bereits bei einem Mitglied hinterlegt");
   Member m=new Member(); m.setFirstName(first); m.setLastName(last); m.setName(first+" "+last); m.setEmail(email); m.setActive(false); m = members.save(m);
   // A self-registration is always a normal member until an administrator approves it.
   AppUser u=new AppUser(); u.setUsername(email); u.setPasswordHash(encoder.encode(req.password())); u.setRole(Role.MEMBER); u.setEnabled(false); u.setRegistrationApproved(false); u.setMember(m);
   m.setUser(u); users.save(u);
   return new RegistrationResponse("Registrierung erfolgreich. Nach der Freischaltung durch den Administrator kannst du dich anmelden.");
 }
 public record RegistrationRequest(String firstName,String lastName,String email,String password){}
 public record RegistrationResponse(String message){}
 private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
 private String normalizeEmail(String v){String x=trim(v);return x==null?null:x.toLowerCase(Locale.ROOT);}
 public record LoginRequest(String username,String password){} public record LoginResponse(String token,String username,String role,Long memberId,String memberName){}
}
