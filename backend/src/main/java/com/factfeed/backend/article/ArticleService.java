package com.factfeed.backend.article;

import com.factfeed.backend.ai.AIService;
import com.factfeed.backend.model.dto.ArticleDTO;
import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.dto.SummarizationResponseDTO;
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
import org.springframework.data.domain.PageRequest;
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
    private final AIService aiService;

    public List<Article> processScrapingResult(ScrapingResultDTO scrapingResult) {
        return processScrapingResult(scrapingResult, true); // Default to automated AI summarization
    }

    /**
     * Process scraping result with optional AI summarization
     */
    public List<Article> processScrapingResult(ScrapingResultDTO scrapingResult, boolean enableAISummarization) {
        log.info("Processing scraping result from {} with {} articles (AI summarization: {})",
                scrapingResult.getSiteName(), scrapingResult.getArticles().size(), enableAISummarization);

        // Pre-fetch existing URLs to avoid per-row existence queries
        List<String> incomingUrls = scrapingResult.getArticles().stream()
                .map(ArticleDTO::getUrl)
                .filter(u -> u != null && !u.trim().isEmpty())
                .map(String::trim)
                .toList();
        List<String> existingUrls = articleRepository.findExistingUrls(incomingUrls);

        // Filter out duplicates and create lightweight DTOs for AI processing
        List<ArticleDTO> validArticles = new ArrayList<>();
        List<ArticleLightDTO> lightDTOs = new ArrayList<>();

        for (ArticleDTO articleDTO : scrapingResult.getArticles()) {
            try {
                String cleanUrl = articleDTO.getUrl() != null ? articleDTO.getUrl().trim() : null;
                if (cleanUrl == null || existingUrls.contains(cleanUrl)) {
                    log.debug("Skipping duplicate or invalid URL: {}", cleanUrl);
                    continue;
                }

                // Validate article before processing
                validateArticleDTO(articleDTO);
                validArticles.add(articleDTO);

                // Create lightweight DTO for AI processing (with temporary ID)
                if (enableAISummarization) {
                    lightDTOs.add(new ArticleLightDTO(
                            (long) validArticles.size(), // Temporary ID for mapping
                            articleDTO.getTitle(), 
                            articleDTO.getContent()
                    ));
                }
            } catch (Exception e) {
                log.error("Error validating article {}: {}", articleDTO.getUrl(), e.getMessage());
            }
        }

        if (validArticles.isEmpty()) {
            log.info("No new valid articles to save");
            return new ArrayList<>();
        }

        // Get AI summaries for all valid articles
        List<SummarizationResponseDTO> summaries = new ArrayList<>();
        if (enableAISummarization && !lightDTOs.isEmpty()) {
            log.info("Getting AI summaries for {} articles", lightDTOs.size());
            summaries = aiService.summarizeArticles(lightDTOs);
        }

        // Now create Article entities and apply summaries
        List<Article> toSave = new ArrayList<>();
        for (int i = 0; i < validArticles.size(); i++) {
            ArticleDTO articleDTO = validArticles.get(i);
            try {
                // Resolve source for each article
                NewsSource sourceForArticle = parseFromUrl(articleDTO.getUrl());
                if (sourceForArticle == null) {
                    sourceForArticle = resolveNewsSource(scrapingResult.getSiteName(), Arrays.asList(articleDTO));
                }

                // Create article entity
                Article article = createArticleFromDTOWithoutSaving(articleDTO, sourceForArticle);
                
                // Apply AI summary if available
                if (enableAISummarization && i < summaries.size()) {
                    SummarizationResponseDTO summary = summaries.get(i);
                    if (summary.isSuccess() && summary.getSummary() != null) {
                        article.setSummarizedContent(summary.getSummary());
                        log.debug("Applied AI summary to article: {}", article.getTitle());
                    } else {
                        log.warn("AI summarization failed for article: {} - {}", 
                                article.getTitle(), summary.getError());
                    }
                }

                if (isValidArticle(article)) {
                    toSave.add(article);
                }
            } catch (Exception e) {
                log.error("Error processing article {}: {}", articleDTO.getUrl(), e.getMessage());
            }
        }

        if (toSave.isEmpty()) {
            log.info("No valid articles to save after processing");
            return new ArrayList<>();
        }

        // Save all articles with summaries in a single transaction
        List<Article> saved = articleRepository.saveAll(toSave);
        log.info("Successfully saved {} articles with AI summarization (if enabled)", saved.size());
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
        return createArticleFromDTOWithoutSaving(articleDTO, source, cleanUrl);
    }

    /**
     * Create Article entity from DTO without saving to database
     */
    private Article createArticleFromDTOWithoutSaving(ArticleDTO articleDTO, NewsSource source) {
        String cleanUrl = articleDTO.getUrl().trim();
        return createArticleFromDTOWithoutSaving(articleDTO, source, cleanUrl);
    }

    private Article createArticleFromDTOWithoutSaving(ArticleDTO articleDTO, NewsSource source, String cleanUrl) {
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

    /**
     * Process articles with AI summarization before saving
     */
    public List<Article> processScrapingResultWithSummarization(ScrapingResultDTO scrapingResult) {
        log.info("Processing scraping result with AI summarization from {} with {} articles",
                scrapingResult.getSiteName(), scrapingResult.getArticles().size());

        // First, process articles normally to get entity objects
        List<Article> articles = processScrapingResult(scrapingResult);
        
        if (articles.isEmpty()) {
            return articles;
        }

        // Create light DTOs for AI processing
        List<ArticleLightDTO> lightDTOs = articles.stream()
                .map(article -> new ArticleLightDTO(article.getId(), article.getTitle(), article.getContent()))
                .toList();

        // Get AI summaries
        List<SummarizationResponseDTO> summaries = aiService.summarizeArticles(lightDTOs);
        
        // Update articles with summaries and save again
        for (SummarizationResponseDTO summary : summaries) {
            if (summary.isSuccess() && summary.getArticleId() != null) {
                articles.stream()
                        .filter(article -> article.getId().equals(summary.getArticleId()))
                        .findFirst()
                        .ifPresent(article -> article.setSummarizedContent(summary.getSummary()));
            }
        }

        // Save updated articles
        List<Article> savedWithSummaries = articleRepository.saveAll(articles);
        log.info("Successfully processed {} articles with AI summaries", savedWithSummaries.size());
        
        return savedWithSummaries;
    }

    /**
     * Summarize existing unsummarized articles
     */
    @Transactional
    public List<SummarizationResponseDTO> summarizeUnsummarizedArticles(int limit) {
        log.info("Starting summarization of unsummarized articles (limit: {})", limit);
        
        List<ArticleLightDTO> unsummarized = articleRepository.findUnsummarizedArticles(
                PageRequest.of(0, limit));
        
        if (unsummarized.isEmpty()) {
            log.info("No unsummarized articles found");
            return new ArrayList<>();
        }

        List<SummarizationResponseDTO> results = aiService.summarizeArticles(unsummarized);
        
        // Update articles with summaries
        for (SummarizationResponseDTO result : results) {
            if (result.isSuccess() && result.getArticleId() != null) {
                articleRepository.findById(result.getArticleId())
                        .ifPresent(article -> {
                            article.setSummarizedContent(result.getSummary());
                            articleRepository.save(article);
                        });
            }
        }
        
        return results;
    }

    /**
     * Get recent articles for clustering analysis
     */
    public List<ArticleLightDTO> getRecentArticlesForClustering(int hours) {
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        return articleRepository.findRecentArticlesLight(fromDate);
    }

    public Page<Article> searchArticles(String keyword, Pageable pageable) {
        return articleRepository.searchByKeyword(keyword, pageable);
    }
}