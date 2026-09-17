package de.bierverein.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AppOrderService {
    private final AppUserRepository users;
    private final MemberRepository members;
    private final ArticleRepository articles;
    private final OrderRepository orders;
    private final AppCartService cart;

    public AppOrderService(AppUserRepository users, MemberRepository members,
                           ArticleRepository articles, OrderRepository orders, AppCartService cart) {
        this.users = users;
        this.members = members;
        this.articles = articles;
        this.orders = orders;
        this.cart = cart;
    }

    @Transactional
    public AppDtos.OrderResponse create(Authentication auth) {
        Member current = currentMember(auth);
        Member member = members.findByIdForUpdate(current.getId()).orElseThrow();
        List<AppDtos.OrderItemRequest> cartItems = cart.get(auth).items().stream()
                .map(item -> new AppDtos.OrderItemRequest(item.articleId(), item.quantity()))
                .toList();
        if (cartItems.isEmpty()) {
            throw bad("Warenkorb ist leer");
        }

        java.util.LinkedHashMap<Long, Integer> quantities = new java.util.LinkedHashMap<>();
        for (AppDtos.OrderItemRequest requested : cartItems) {
            if (requested == null || requested.articleId() == null || requested.quantity() <= 0 || requested.quantity() > 99) {
                throw bad("Ungültige Position");
            }
            int totalQuantity = quantities.getOrDefault(requested.articleId(), 0) + requested.quantity();
            if (totalQuantity > 99) throw bad("Maximal 99 Stück je Artikel pro Bestellung");
            quantities.put(requested.articleId(), totalQuantity);
        }

        BigDecimal total = BigDecimal.ZERO;
        Order order = new Order();
        order.setMember(member);
        order.setCreatedBy(auth.getName());
        order.setStatus(OrderStatus.NEW);

        for (var entry : quantities.entrySet()) {
            Article article = articles.findByIdForUpdate(entry.getKey())
                    .orElseThrow(() -> notFound("Artikel nicht gefunden: " + entry.getKey()));
            int quantity = entry.getValue();
            if (!article.isActive()) throw conflict("Artikel ist nicht verfügbar: " + article.getName());
            BigDecimal needed = BigDecimal.valueOf(quantity);
            if (article.getStock().compareTo(needed) < 0) {
                throw conflict("Bestand nicht ausreichend für " + article.getName() + " (Bestand: " + article.getStock() + ")");
            }
            BigDecimal line = article.getPrice().multiply(needed).setScale(2);
            OrderItem item = new OrderItem();
            item.setArticle(article);
            item.setQuantity(quantity);
            item.setUnitPrice(article.getPrice());
            item.setTotal(line);
            order.addItem(item);
            total = total.add(line);
        }

        if (member.getBalance().compareTo(total) < 0) {
            throw conflict("Guthaben reicht nicht aus. Verfügbar: " + member.getBalance() + " €");
        }

        // Only after all validation has succeeded do we mutate stock and balance.
        for (OrderItem item : order.getItems()) {
            Article article = item.getArticle();
            article.setStock(article.getStock().subtract(BigDecimal.valueOf(item.getQuantity())));
            articles.save(article);
        }
        member.setBalance(member.getBalance().subtract(total));
        order.setTotal(total);
        AppDtos.OrderResponse response = toResponse(orders.save(order));
        cart.clear(auth);
        return response;
    }

    @Transactional(readOnly = true)
    public List<AppDtos.OrderResponse> mine(Authentication auth) {
        Member member = currentMember(auth);
        return orders.findTop50ByMemberIdOrderByCreatedAtDesc(member.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AppDtos.OrderResponse one(Long id, Authentication auth) {
        Member member = currentMember(auth);
        Order order = orders.findById(id).orElseThrow(() -> notFound("Bestellung nicht gefunden"));
        if (!order.getMember().getId().equals(member.getId())) throw notFound("Bestellung nicht gefunden");
        return toResponse(order);
    }

    @Transactional
    public AppDtos.OrderResponse cancel(Long id, Authentication auth) {
        Member member = currentMember(auth);
        Order order = orders.findByIdForUpdate(id).orElseThrow(() -> notFound("Bestellung nicht gefunden"));
        if (!order.getMember().getId().equals(member.getId())) throw notFound("Bestellung nicht gefunden");
        if (order.getStatus() != OrderStatus.NEW && order.getStatus() != OrderStatus.CONFIRMED) {
            throw conflict("Die Bestellung kann in diesem Status nicht mehr per App storniert werden.");
        }

        Member lockedMember = members.findByIdForUpdate(member.getId()).orElseThrow();
        for (OrderItem item : order.getItems()) {
            if (item.getArticle() == null) {
                throw conflict("Diese ältere Bestellung kann nicht über die neue App storniert werden.");
            }
            Article article = articles.findById(item.getArticle().getId()).orElseThrow();
            article.setStock(article.getStock().add(BigDecimal.valueOf(item.getQuantity())));
            articles.save(article);
        }
        lockedMember.setBalance(lockedMember.getBalance().add(order.getTotal()));
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());
        order.setCancelledBy(auth.getName());
        order.setCancellationReason("Stornierung durch Mitglied per App");
        return toResponse(orders.save(order));
    }

    private Member currentMember(Authentication auth) {
        AppUser user = users.findByUsernameIgnoreCase(auth.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Benutzer nicht gefunden"));
        if (!user.isEnabled() || !user.isRegistrationApproved()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Benutzer ist nicht freigeschaltet");
        }
        if (user.getMember() == null) throw bad("Benutzer ist keinem Mitglied zugeordnet");
        Member member = members.findById(user.getMember().getId())
                .orElseThrow(() -> notFound("Mitglied nicht gefunden"));
        if (!member.isActive()) throw conflict("Mitglied ist inaktiv");
        return member;
    }

    private AppDtos.OrderResponse toResponse(Order o) {
        List<AppDtos.OrderItemResponse> items = o.getItems().stream().map(i -> {
            Article a = i.getArticle();
            if (a != null) return new AppDtos.OrderItemResponse(a.getId(), a.getName(), a.getType(), i.getQuantity(), i.getUnitPrice(), i.getTotal());
            Drink d = i.getDrink();
            return new AppDtos.OrderItemResponse(null, d == null ? "Unbekannter Artikel" : d.getName(), ArticleType.DRINK, i.getQuantity(), i.getUnitPrice(), i.getTotal());
        }).toList();
        return new AppDtos.OrderResponse(o.getId(), o.getMember().getId(), o.getTotal(), o.getCreatedAt(), o.getStatus(), o.getCancelledAt(), o.getCancellationReason(), items);
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
