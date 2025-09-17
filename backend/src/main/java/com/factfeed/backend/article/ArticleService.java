package com.factfeed.backend.article;

import com.factfeed.backend.model.dto.ArticleDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ArticleService {

    private final ArticleRepository articleRepository;

    public List<Article> processScrapingResult(ScrapingResultDTO scrapingResult) {
        log.info("Processing scraping result from {} with {} articles",
                scrapingResult.getSiteName(), scrapingResult.getArticles().size());

        // Resolve news source from siteName or URLs (enum-based)
        NewsSource source = resolveNewsSource(scrapingResult.getSiteName(), scrapingResult.getArticles());

        List<Article> savedArticles = new ArrayList<>();

        for (ArticleDTO articleDTO : scrapingResult.getArticles()) {
            try {
                Article article = processArticle(articleDTO, source);
                if (article != null) {
                    savedArticles.add(article);
                }
            } catch (Exception e) {
                log.error("Error processing article {}: {}", articleDTO.getUrl(), e.getMessage());
            }
        }

        log.info("Successfully saved {} articles from {}", savedArticles.size(), source);
        return savedArticles;
    }

    public Article processArticle(ArticleDTO articleDTO, NewsSource source) {
        validateArticleDTO(articleDTO);
        if (source == null) {
            throw new IllegalArgumentException("NewsSource cannot be null");
        }

        String cleanUrl = articleDTO.getUrl().trim();

        // Check for duplicate URL
        Optional<Article> existingByUrl = articleRepository.findByUrl(cleanUrl);
        if (existingByUrl.isPresent()) {
            log.debug("Article with URL {} already exists", cleanUrl);
            return existingByUrl.get();
        }

        // Create new article
        Article article = createArticleFromDTO(articleDTO, source, cleanUrl);

        if (!isValidArticle(article)) {
            log.warn("Invalid article data for URL: {}", cleanUrl);
            throw new IllegalArgumentException("Article validation failed for URL: " + cleanUrl);
        }

        try {
            return articleRepository.save(article);
        } catch (Exception e) {
            log.error("Error saving article to database: {}", e.getMessage());
            throw new RuntimeException("Failed to save article: " + e.getMessage(), e);
        }
    }

    private void validateArticleDTO(ArticleDTO articleDTO) {
        if (articleDTO == null) {
            throw new IllegalArgumentException("ArticleDTO cannot be null");
        }
        if (articleDTO.getUrl() == null || articleDTO.getUrl().trim().isEmpty()) {
            throw new IllegalArgumentException("Article URL cannot be null or empty");
        }
        if (articleDTO.getTitle() == null || articleDTO.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Article title cannot be null or empty");
        }
        if (articleDTO.getContent() == null || articleDTO.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Article content cannot be null or empty");
        }
    }

    private Article createArticleFromDTO(ArticleDTO articleDTO, NewsSource source, String cleanUrl) {
        Article article = new Article();
        article.setTitle(articleDTO.getTitle().trim());
        article.setContent(articleDTO.getContent().trim());
        article.setUrl(cleanUrl);
        article.setAuthor(articleDTO.getAuthor() != null ? articleDTO.getAuthor().trim() : null);
        article.setImageUrl(articleDTO.getImageUrl() != null ? articleDTO.getImageUrl().trim() : null);
        article.setSource(source);
        article.setCategory(articleDTO.getCategory() != null ? articleDTO.getCategory().trim() : null);
        article.setWordCount(countWords(articleDTO.getContent()));
        article.setScrapedAt(LocalDateTime.now());

        // Parse published date with error handling
        try {
            LocalDateTime publishedDate = articleDTO.getParsedPublishedDate();
            article.setPublishedDate(publishedDate != null ? publishedDate : LocalDateTime.now());
        } catch (Exception e) {
            log.warn("Error parsing published date for article {}: {}", cleanUrl, e.getMessage());
            article.setPublishedDate(LocalDateTime.now());
        }

        return article;
    }

    private NewsSource resolveNewsSource(String siteName, List<ArticleDTO> articles) {
        if (siteName != null && !siteName.trim().isEmpty()) {
            String normalized = normalizeSourceKey(siteName);
            for (NewsSource ns : NewsSource.values()) {
                if (ns.name().equalsIgnoreCase(normalized)) {
                    return ns;
                }
            }
        }
        if (articles != null) {
            for (ArticleDTO dto : articles) {
                NewsSource parsed = parseFromUrl(dto.getUrl());
                if (parsed != null) return parsed;
            }
        }
        throw new IllegalArgumentException("Unknown news source: " + siteName);
    }

    private String normalizeSourceKey(String s) {
        return s.replaceAll("[^A-Za-z]", "").toUpperCase();
    }

    private NewsSource parseFromUrl(String url) {
        if (url == null) return null;
        String u = url.toLowerCase();
        if (u.contains("prothomalo.com")) return NewsSource.PROTHOMALO;
        if (u.contains("ittefaq.com.bd")) return NewsSource.ITTEFAQ;
        return null;
    }

    private int countWords(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }
        return content.trim().split("\\s+").length;
    }

    private boolean isValidArticle(Article article) {
        return article.getTitle() != null && article.getTitle().length() >= 5 &&
                article.getContent() != null && article.getContent().length() >= 50 &&
                article.getUrl() != null && article.getUrl().startsWith("http") &&
                article.getSource() != null;
    }

    public Page<Article> getAllArticles(Pageable pageable) {
        return articleRepository.findAll(pageable);
    }

    public Page<Article> getRecentArticles(int hours, Pageable pageable) {
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        return articleRepository.findRecentArticles(fromDate, pageable);
    }

    public Page<Article> getArticlesByCategory(String category, Pageable pageable) {
        return articleRepository.findByCategory(category, pageable);
    }

    public Page<Article> getArticlesBySource(NewsSource source, Pageable pageable) {
        return articleRepository.findBySource(source, pageable);
    }

    public Page<Article> searchArticles(String keyword, Pageable pageable) {
        return articleRepository.searchByKeyword(keyword, pageable);
    }
}