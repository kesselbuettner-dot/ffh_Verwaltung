package de.bierverein.api;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppMenuService {
    private final ArticleRepository articles;

    public AppMenuService(ArticleRepository articles) {
        this.articles = articles;
    }

    public List<AppDtos.MenuArticle> drinks() {
        return articles.findAllByOrderByActiveDescNameAsc().stream()
                .filter(Article::isActive)
                .filter(a -> a.getType() == ArticleType.DRINK)
                .filter(a -> a.getStock().compareTo(java.math.BigDecimal.ZERO) > 0)
                .map(AppDtos.MenuArticle::from)
                .toList();
    }

    public List<AppDtos.MenuArticle> food() {
        return articles.findAllByOrderByActiveDescNameAsc().stream()
                .filter(Article::isActive)
                .filter(a -> a.getType() == ArticleType.FOOD)
                .filter(a -> a.getStock().compareTo(java.math.BigDecimal.ZERO) > 0)
                .map(AppDtos.MenuArticle::from)
                .toList();
    }
}
