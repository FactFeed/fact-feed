package com.factfeed.backend.article;

import com.factfeed.backend.model.dto.ArticleDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
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

    // Reusable date formatters to avoid repeated allocations
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_ZONED_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
    );
    private final ArticleRepository articleRepository;

    public List<Article> processScrapingResult(ScrapingResultDTO scrapingResult) {
        log.info("Processing scraping result from {} with {} articles",
                scrapingResult.getSiteName(), scrapingResult.getArticles().size());

        // Pre-fetch existing URLs to avoid per-row existence queries
        List<String> incomingUrls = scrapingResult.getArticles().stream()
                .map(ArticleDTO::getUrl)
                .filter(u -> u != null && !u.trim().isEmpty())
                .map(String::trim)
                .toList();
        List<String> existingUrls = articleRepository.findExistingUrls(incomingUrls);

        List<Article> toSave = new ArrayList<>();

        for (ArticleDTO articleDTO : scrapingResult.getArticles()) {
            try {
                // Resolve per-article source to handle aggregated results from multiple sources
                NewsSource sourceForArticle = parseFromUrl(articleDTO.getUrl());
                if (sourceForArticle == null) {
                    // Fallback to siteName when URL parsing fails
                    sourceForArticle = resolveNewsSource(scrapingResult.getSiteName(), Arrays.asList(articleDTO));
                }

                String cleanUrl = articleDTO.getUrl() != null ? articleDTO.getUrl().trim() : null;
                if (cleanUrl == null || existingUrls.contains(cleanUrl)) {
                    log.debug("Skipping duplicate or invalid URL: {}", cleanUrl);
                    continue;
                }

                Article article = processArticle(articleDTO, sourceForArticle);
                if (article != null) toSave.add(article);
            } catch (Exception e) {
                log.error("Error processing article {}: {}", articleDTO.getUrl(), e.getMessage());
            }
        }
        if (toSave.isEmpty()) {
            log.info("No new articles to save");
            return new ArrayList<>();
        }
        List<Article> saved = articleRepository.saveAll(toSave);
        log.info("Successfully saved {} new articles", saved.size());
        return saved;
    }

    @Transactional
    public Article processArticleIndividually(ArticleDTO articleDTO, NewsSource source) {
        try {
            return processArticle(articleDTO, source);
        } catch (Exception e) {
            log.error("Failed to process article {}: {}", articleDTO.getUrl(), e.getMessage());
            return null;
        }
    }

    public Article processArticle(ArticleDTO articleDTO, NewsSource source) {
        try {
            validateArticleDTO(articleDTO);
            if (source == null) {
                throw new IllegalArgumentException("News source cannot be null");
            }

            String cleanUrl = articleDTO.getUrl().trim();

            // Check for duplicate URL
            Optional<Article> existingByUrl = articleRepository.findByUrl(cleanUrl);
            if (existingByUrl.isPresent()) {
                log.debug("Article with URL {} already exists, skipping", cleanUrl);
                return null;
            }

            // Create new article
            Article article = createArticleFromDTO(articleDTO, source, cleanUrl);

            if (!isValidArticle(article)) {
                log.warn("Article validation failed for URL: {}", cleanUrl);
                return null;
            }

            return articleRepository.save(article);

        } catch (Exception e) {
            log.error("Error processing article {}: {}", articleDTO.getUrl(), e.getMessage());
            return null;
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
        article.setTitle(truncateString(articleDTO.getTitle(), 500));
        article.setContent(articleDTO.getContent().trim());
        article.setUrl(cleanUrl);
        article.setAuthor(truncateString(articleDTO.getAuthor(), 200));
        article.setAuthorLocation(truncateString(articleDTO.getAuthorLocation(), 200));
        article.setImageUrl(articleDTO.getImageUrl() != null ? articleDTO.getImageUrl().trim() : null);
        article.setImageCaption(truncateString(articleDTO.getImageCaption(), 500));
        article.setSource(source);
        article.setCategory(truncateString(articleDTO.getCategory(), 100));
        article.setTags(truncateString(articleDTO.getTags(), 500));

        // Calculate word count from content
        article.setWordCount(countWords(articleDTO.getContent()));

        // scrapedAt and dbUpdatedAt are handled by JPA annotations

        // Parse and set date fields
        parseAndSetDate(articleDTO, article, "publishedAt", cleanUrl, LocalDateTime.now(), true);
        parseAndSetDate(articleDTO, article, "updatedAt", cleanUrl, null, false);

        return article;
    }

    /**
     * Helper to parse and set date fields to reduce duplication.
     */
    private void parseAndSetDate(ArticleDTO dto, Article article, String fieldName, String cleanUrl, LocalDateTime defaultValue, boolean warnOnError) {
        String dateStr = null;
        if ("publishedAt".equals(fieldName)) {
            dateStr = dto.getPublishedAt();
        } else if ("updatedAt".equals(fieldName)) {
            dateStr = dto.getUpdatedAt();
        }

        if (dateStr != null && !dateStr.trim().isEmpty()) {
            try {
                LocalDateTime parsedDate = parseDateTime(dateStr);
                if ("publishedAt".equals(fieldName)) {
                    article.setPublishedAt(parsedDate);
                } else {
                    article.setUpdatedAt(parsedDate);
                }
            } catch (Exception e) {
                if (warnOnError) {
                    log.warn("Error parsing {} date for article {}: {}", fieldName, cleanUrl, e.getMessage());
                    if ("publishedAt".equals(fieldName) && defaultValue != null) {
                        article.setPublishedAt(defaultValue);
                    }
                } else {
                    log.debug("Could not parse {} date for article {}: {}", fieldName, cleanUrl, e.getMessage());
                }
            }
        } else {
            if ("publishedAt".equals(fieldName) && defaultValue != null) {
                article.setPublishedAt(defaultValue);
            }
        }
    }

    /**
     * Parse date string in various formats
     */
    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        String cleanDateStr = dateStr.trim();

        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // Try parsing as ZonedDateTime first (for offset formats)
                if (cleanDateStr.contains("+") || cleanDateStr.contains("Z")) {
                    return ZonedDateTime.parse(cleanDateStr, formatter).toLocalDateTime();
                } else {
                    return LocalDateTime.parse(cleanDateStr, formatter);
                }
            } catch (Exception ignored) {
                // Continue to next formatter
            }
        }

        // If all parsing fails, return current time
        log.warn("Could not parse date string: {}", cleanDateStr);
        return LocalDateTime.now();
    }

    private NewsSource resolveNewsSource(String siteName, List<ArticleDTO> articles) {
        if (siteName != null && !siteName.trim().isEmpty()) {
            // Try to map by name directly
            NewsSource byName = NewsSource.fromName(siteName);
            if (byName != null) return byName;

            // Fallback: try normalized matching to enum names
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

        for (NewsSource source : NewsSource.values()) {
            if (source.matchesUrl(url)) {
                return source;
            }
        }
        return null;
    }

    private String truncateString(String str, int maxLength) {
        if (str == null) return null;
        String trimmed = str.trim();
        if (trimmed.length() <= maxLength) return trimmed;
        return trimmed.substring(0, maxLength - 3) + "...";
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