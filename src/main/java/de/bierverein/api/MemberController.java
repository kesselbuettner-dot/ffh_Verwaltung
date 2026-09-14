package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import java.util.*;
@RestController @RequestMapping("/api/members") public class MemberController { private final MemberService service; public MemberController(MemberService s){service=s;}
 @GetMapping @PreAuthorize("hasAnyRole('ADMIN','VORSTAND','KASSENWART','THEKE')") public List<MemberDtos.MemberResponse> all(){return service.all();}
 @GetMapping("/me") @PreAuthorize("hasRole('MEMBER')") public MemberDtos.MemberResponse me(Authentication a){ return service.all().stream().filter(x->x.username()!=null && x.username().equals(a.getName())).findFirst().orElseThrow(); }
 @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','VORSTAND','KASSENWART','THEKE')") public MemberDtos.MemberResponse one(@PathVariable Long id){return service.one(id);}
 @PostMapping @PreAuthorize("hasAnyRole('ADMIN','VORSTAND')") public MemberDtos.MemberResponse create(@RequestBody MemberDtos.MemberRequest r, Authentication a){return service.create(r,a);}
 @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','VORSTAND')") public MemberDtos.MemberResponse update(@PathVariable Long id,@RequestBody MemberDtos.MemberRequest r, Authentication a){return service.update(id,r,a);}
 @PatchMapping("/{id}/balance") @PreAuthorize("hasAnyRole('ADMIN','VORSTAND','KASSENWART')") public MemberDtos.MemberResponse balance(@PathVariable Long id,@RequestBody MemberDtos.BalanceRequest r){return service.balance(id,r);}
}
