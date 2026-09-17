package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/app/orders")
@PreAuthorize("isAuthenticated()")
public class AppOrderController {
    private final AppOrderService service;

    public AppOrderController(AppOrderService service) {
        this.service = service;
    }

    @PostMapping
    public AppDtos.OrderResponse create(Authentication auth) {
        return service.create(auth);
    }

    @GetMapping
    public List<AppDtos.OrderResponse> mine(Authentication auth) {
        return service.mine(auth);
    }

    @GetMapping("/{id}")
    public AppDtos.OrderResponse one(@PathVariable Long id, Authentication auth) {
        return service.one(id, auth);
    }

    @PostMapping("/{id}/cancel")
    public AppDtos.OrderResponse cancel(@PathVariable Long id, Authentication auth) {
        return service.cancel(id, auth);
    }
}
