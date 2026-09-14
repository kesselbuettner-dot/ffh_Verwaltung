package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.crypto.password.PasswordEncoder; import java.util.*;
@RestController @RequestMapping("/api/admin/users") @PreAuthorize("hasRole('ADMIN')") public class UserAdminController { private final AppUserRepository users; private final PasswordEncoder encoder; public UserAdminController(AppUserRepository u,PasswordEncoder e){users=u;encoder=e;}
 @GetMapping public List<UserDto> all(){return users.findAll().stream().map(u->new UserDto(u.getId(),u.getUsername(),u.getRole(),u.isEnabled(),u.getMember()==null?null:u.getMember().getId(),u.getMember()==null?null:u.getMember().getName())).toList();}
 public record UserDto(Long id,String username,Role role,boolean enabled,Long memberId,String memberName){}
}
