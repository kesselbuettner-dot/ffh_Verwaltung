package de.bierverein.api;

import java.math.BigDecimal;

public final class ArticleDtos {
    private ArticleDtos() {}

    public record ArticleRequest(
            String name,
            String articleNumber,
            String shortName,
            String category,
            BigDecimal price,
            BigDecimal stock,
            String searchTerm,
            boolean active,
            ArticleType type
    ) {}

    public record ActiveRequest(
            boolean active,
            ArticleType type
    ) {}
}
