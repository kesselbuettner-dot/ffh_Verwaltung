package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/cart")
@PreAuthorize("isAuthenticated()")
public class AppCartController {
    private final AppCartService service;

    public AppCartController(AppCartService service) {
        this.service = service;
    }

    @GetMapping
    public AppDtos.CartResponse get(Authentication auth) {
        return service.get(auth);
    }

    @PutMapping
    public AppDtos.CartResponse replace(@RequestBody List<AppDtos.OrderItemRequest> items, Authentication auth) {
        return service.replace(items, auth);
    }

    @DeleteMapping
    public AppDtos.CartResponse clear(Authentication auth) {
        return service.clear(auth);
    }
}
