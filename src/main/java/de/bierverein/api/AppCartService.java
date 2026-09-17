package de.bierverein.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class AppCartService {
    private static final Duration TTL = Duration.ofHours(24);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AppUserRepository users;
    private final ArticleRepository articles;

    public AppCartService(StringRedisTemplate redis, ObjectMapper mapper,
                          AppUserRepository users, ArticleRepository articles) {
        this.redis = redis;
        this.mapper = mapper;
        this.users = users;
        this.articles = articles;
    }

    public AppDtos.CartResponse get(Authentication auth) {
        List<AppDtos.OrderItemRequest> items = load(auth);
        return toResponse(items);
    }

    public AppDtos.CartResponse replace(List<AppDtos.OrderItemRequest> items, Authentication auth) {
        validate(items);
        save(auth, items);
        return toResponse(items);
    }

    public AppDtos.CartResponse clear(Authentication auth) {
        redis.delete(key(auth));
        return new AppDtos.CartResponse(List.of(), BigDecimal.ZERO);
    }

    private List<AppDtos.OrderItemRequest> load(Authentication auth) {
        String json = redis.opsForValue().get(key(auth));
        if (json == null || json.isBlank()) return List.of();
        try {
            return mapper.readValue(json, new TypeReference<List<AppDtos.OrderItemRequest>>() {});
        } catch (Exception e) {
            redis.delete(key(auth));
            return List.of();
        }
    }

    private void save(Authentication auth, List<AppDtos.OrderItemRequest> items) {
        try {
            redis.opsForValue().set(key(auth), mapper.writeValueAsString(items), TTL);
        } catch (Exception e) {
            throw new IllegalStateException("Warenkorb konnte nicht gespeichert werden", e);
        }
    }

    private String key(Authentication auth) {
        AppUser user = users.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden"));
        return "ffh:app:cart:" + user.getId();
    }

    private void validate(List<AppDtos.OrderItemRequest> items) {
        if (items == null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Warenkorb ist leer");
        var merged = new java.util.LinkedHashMap<Long, Integer>();
        for (var item : items) {
            if (item == null || item.articleId() == null || item.quantity() <= 0 || item.quantity() > 99) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Ungültige Warenkorbposition");
            }
            int q = merged.getOrDefault(item.articleId(), 0) + item.quantity();
            if (q > 99) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Maximal 99 Stück je Artikel");
            if (!articles.existsById(item.articleId())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Artikel nicht gefunden: " + item.articleId());
            merged.put(item.articleId(), q);
        }
    }

    private AppDtos.CartResponse toResponse(List<AppDtos.OrderItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        List<AppDtos.CartItem> result = new ArrayList<>();
        for (var item : items) {
            Article article = articles.findById(item.articleId()).orElse(null);
            if (article == null) continue;
            BigDecimal line = article.getPrice().multiply(BigDecimal.valueOf(item.quantity())).setScale(2);
            total = total.add(line);
            result.add(new AppDtos.CartItem(article.getId(), article.getName(), article.getType(), item.quantity(), article.getPrice(), line,
                    article.isActive() && article.getStock().compareTo(BigDecimal.ZERO) > 0));
        }
        return new AppDtos.CartResponse(result, total);
    }
}
