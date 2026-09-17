package de.bierverein.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class AppDtos {
    private AppDtos() {}

    public record MenuArticle(Long id, String name, String category, BigDecimal price,
                              String articleNumber, boolean available, ArticleType type) {
        static MenuArticle from(Article a) {
            return new MenuArticle(a.getId(), a.getName(), a.getCategory(), a.getPrice(),
                    a.getArticleNumber(), a.isActive() && a.getStock().compareTo(BigDecimal.ZERO) > 0, a.getType());
        }
    }

    public record MenuResponse(List<MenuArticle> drinks, List<MenuArticle> food) {}

    public record CartItem(Long articleId, String name, ArticleType type, int quantity, BigDecimal unitPrice, BigDecimal total, boolean available) {}
    public record CartResponse(List<CartItem> items, BigDecimal total) {}

    public record OrderItemRequest(Long articleId, int quantity) {}
    public record OrderRequest(List<OrderItemRequest> items) {}

    public record OrderItemResponse(Long articleId, String name, ArticleType type,
                                    int quantity, BigDecimal unitPrice, BigDecimal total) {}

    public record OrderResponse(Long id, Long memberId, BigDecimal total, Instant createdAt,
                                 OrderStatus status, Instant cancelledAt, String cancellationReason,
                                 List<OrderItemResponse> items) {}

    public record BalanceResponse(BigDecimal balance) {}
}
