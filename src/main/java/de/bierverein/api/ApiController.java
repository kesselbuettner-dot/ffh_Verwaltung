package de.ffh_verwaltung.api;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.math.BigDecimal; import java.util.List;

@RestController @RequestMapping("/api")
public class ApiController {
 private final MemberRepository members; private final DrinkRepository drinks; private final BookingRepository bookings;
 public ApiController(MemberRepository m,DrinkRepository d,BookingRepository b){members=m;drinks=d;bookings=b;}

 @GetMapping("/members") @PreAuthorize("hasAnyRole('ADMIN','THEKE')") public List<Member> members(){return members.findAll();}
 @PostMapping("/members") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
 public Member addMember(@RequestBody Member m){return members.save(m);}

 @GetMapping("/drinks") public List<Drink> drinks(){return drinks.findAll();}
 @PostMapping("/drinks") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED)
 public Drink addDrink(@RequestBody Drink d){return drinks.save(d);}

 @GetMapping("/bookings") @PreAuthorize("hasAnyRole('ADMIN','THEKE')") public List<Booking> bookings(){return bookings.findAll();}
 @PostMapping("/bookings") @PreAuthorize("hasAnyRole('ADMIN','THEKE')")
 @ResponseStatus(HttpStatus.CREATED)
 public Booking addBooking(@RequestBody BookingRequest req){
   Member m=members.findById(req.memberId()).orElseThrow(); Drink d=drinks.findById(req.drinkId()).orElseThrow();
   int q=Math.max(1,req.quantity()); BigDecimal total=d.getPrice().multiply(BigDecimal.valueOf(q));
   Booking b=new Booking(); b.setMember(m);b.setDrink(d);b.setQuantity(q);b.setUnitPrice(d.getPrice());b.setTotal(total);
   Booking saved=bookings.save(b); m.setBalance(m.getBalance().add(total));members.save(m); return saved;
 }
 public record BookingRequest(Long memberId,Long drinkId,int quantity){}
}
