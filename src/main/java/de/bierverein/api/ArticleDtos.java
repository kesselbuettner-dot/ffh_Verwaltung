package de.bierverein.api;

import java.math.BigDecimal;

public final class ArticleDtos {
    private ArticleDtos() {}

    public record ArticleRequest(
            String name,
            String shortName,
            String category,
            BigDecimal price,
            BigDecimal stock,
            String searchTerm,
            boolean active
    ) {}

    public record ActiveRequest(boolean active) {}
}
