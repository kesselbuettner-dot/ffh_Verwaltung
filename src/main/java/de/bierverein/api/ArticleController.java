package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {
    private final ArticleRepository articles;

    public ArticleController(ArticleRepository articles) {
        this.articles = articles;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','VORSTAND','KASSENWART','THEKE')")
    public List<Article> all() {
        return articles.findAllByOrderByActiveDescNameAsc();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','VORSTAND')")
    @ResponseStatus(HttpStatus.CREATED)
    public Article create(@RequestBody ArticleDtos.ArticleRequest req) {
        Article a = new Article();
        apply(a, req);
        return articles.save(a);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','VORSTAND')")
    public Article update(@PathVariable Long id, @RequestBody ArticleDtos.ArticleRequest req) {
        Article a = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikel nicht gefunden"));
        apply(a, req);
        return articles.save(a);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('ADMIN','VORSTAND')")
    public Article setActive(@PathVariable Long id, @RequestBody ArticleDtos.ActiveRequest req) {
        Article a = articles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikel nicht gefunden"));
        a.setActive(req.active());
        return articles.save(a);
    }

    private void apply(Article a, ArticleDtos.ArticleRequest req) {
        if (req.name() == null || req.name().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bezeichnung ist erforderlich");
        }
        if (req.price() == null || req.price().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preis muss 0 oder größer sein");
        }
        if (req.stock() == null || req.stock().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bestand darf nicht negativ sein");
        }
        a.setName(req.name().trim());
        a.setArticleNumber(clean(req.articleNumber()));
        a.setShortName(clean(req.shortName()));
        a.setCategory(clean(req.category()));
        a.setPrice(req.price());
        a.setStock(req.stock());
        a.setSearchTerm(clean(req.searchTerm()));
        a.setActive(req.active());
    }

    private String clean(String value) {
        if (value == null) return null;
        String v = value.trim();
        return v.isEmpty() ? null : v;
    }
}
