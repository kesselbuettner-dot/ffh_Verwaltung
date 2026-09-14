package de.bierverein.api;
import org.springframework.web.bind.annotation.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.http.*; import java.util.*;
@RestController @RequestMapping("/api") public class ApiController { private final DrinkRepository drinks; public ApiController(DrinkRepository d){drinks=d;}
 @GetMapping("/drinks") public List<Drink> drinks(){return drinks.findAll();}
 @PostMapping("/drinks") @PreAuthorize("hasRole('ADMIN')") @ResponseStatus(HttpStatus.CREATED) public Drink addDrink(@RequestBody Drink d){return drinks.save(d);}
}
