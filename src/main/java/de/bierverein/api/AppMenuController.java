package de.bierverein.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/app/menu")
@PreAuthorize("isAuthenticated()")
public class AppMenuController {
    private final AppMenuService menu;

    public AppMenuController(AppMenuService menu) {
        this.menu = menu;
    }

    @GetMapping
    public AppDtos.MenuResponse menu() {
        return new AppDtos.MenuResponse(menu.drinks(), menu.food());
    }

    @GetMapping("/drinks")
    public List<AppDtos.MenuArticle> drinks() {
        return menu.drinks();
    }

    @GetMapping("/food")
    public List<AppDtos.MenuArticle> food() {
        return menu.food();
    }
}
