package com.factfeed.backend.database.service;

import com.factfeed.backend.database.entity.Article;
import com.factfeed.backend.database.repository.ArticleRepository;
import com.factfeed.backend.model.enums.NewsSource;
import com.factfeed.backend.scraper.model.ArticleData;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    @Transactional
    public void storeArticles(List<ArticleData> articles, String siteName) {
        if (articles == null || articles.isEmpty()) {
            log.debug("No articles to store for site: {}", siteName);
            return;
        }

        log.info("Storing {} articles for site: {}", articles.size(), siteName);

        NewsSource source = NewsSource.fromCode(siteName);
        int savedCount = 0;
        int skippedCount = 0;

        for (ArticleData articleData : articles) {
            try {
                if (!articleRepository.existsByUrl(articleData.getUrl())) {
                    Article article = convertToEntity(articleData, source);
                    articleRepository.save(article);
                    savedCount++;
                } else {
                    skippedCount++;
                }
            } catch (Exception e) {
                log.warn("Failed to save article {}: {}", articleData.getUrl(), e.getMessage());
            }
        }

        log.info("Successfully stored {} new articles for {}. Skipped {} duplicates.", 
                savedCount, siteName, skippedCount);
    }

    public Set<String> getExistingUrls(String siteName) {
        NewsSource source = NewsSource.fromCode(siteName);
        return articleRepository.findUrlsBySource(source);
    }

    public List<ArticleData> getArticles(String siteName) {
        NewsSource source = NewsSource.fromCode(siteName);
        List<Article> articles = articleRepository.findBySourceOrderByExtractedAtDesc(source);
        return articles.stream()
                .map(this::convertToArticleData)
                .collect(Collectors.toList());
    }

    public List<ArticleData> getArticles(String siteName, int offset, int limit) {
        NewsSource source = NewsSource.fromCode(siteName);
        Pageable pageable = PageRequest.of(offset / limit, limit);
        Page<Article> articles = articleRepository.findBySourceOrderByExtractedAtDesc(source, pageable);
        return articles.getContent().stream()
                .map(this::convertToArticleData)
                .collect(Collectors.toList());
    }

    public List<ArticleData> getRecentArticles(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Article> articles = articleRepository.findAllByOrderByExtractedAtDesc(pageable);
        return articles.getContent().stream()
                .map(this::convertToArticleData)
                .collect(Collectors.toList());
    }

    public int getArticleCount(String siteName) {
        NewsSource source = NewsSource.fromCode(siteName);
        return (int) articleRepository.countBySource(source);
    }

    public int getTotalArticleCount() {
        return (int) articleRepository.count();
    }

    public LocalDateTime getLastScrapeTime(String siteName) {
        NewsSource source = NewsSource.fromCode(siteName);
        return articleRepository.findLastExtractionTimeBySource(source).orElse(null);
    }

    public List<ArticleData> searchArticles(String query, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        Page<Article> articles = articleRepository.searchByTitleOrContent(query, pageable);
        return articles.getContent().stream()
                .map(this::convertToArticleData)
                .collect(Collectors.toList());
    }

    public Map<String, Object> getStorageStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalArticles = articleRepository.count();
        stats.put("totalArticles", totalArticles);
        stats.put("storageType", "database");

        // Get counts by source
        List<ArticleRepository.SourceCount> sourceCounts = articleRepository.getArticleCountsBySource();
        Map<String, Long> articlesBySite = sourceCounts.stream()
                .collect(Collectors.toMap(
                    sc -> sc.getSource().getCode(),
                    ArticleRepository.SourceCount::getCount
                ));
        stats.put("articlesBySite", articlesBySite);
        stats.put("sitesCount", articlesBySite.size());

        return stats;
    }

    private Article convertToEntity(ArticleData articleData, NewsSource source) {
        Article.ArticleBuilder builder = Article.builder()
                .url(articleData.getUrl())
                .title(articleData.getTitle())
                .content(articleData.getContent())
                .source(source)
                .extractedAt(articleData.getExtractedAt())
                .extractionMethod(articleData.getExtractionMethod())
                .imageUrl(articleData.getImageUrl())
                .imageCaption(articleData.getImageCaption())
                .category(articleData.getCategory());

        // Convert authors list to comma-separated string
        if (articleData.getAuthors() != null && !articleData.getAuthors().isEmpty()) {
            builder.authors(String.join(", ", articleData.getAuthors()));
        }

        // Convert tags list to comma-separated string
        if (articleData.getTags() != null && !articleData.getTags().isEmpty()) {
            builder.tags(String.join(", ", articleData.getTags()));
        }

        // Parse date strings to LocalDateTime
        if (articleData.getPublishedAt() != null) {
            try {
                builder.publishedAt(LocalDateTime.parse(articleData.getPublishedAt(), dateFormatter));
            } catch (Exception e) {
                log.debug("Failed to parse publishedAt date: {}", articleData.getPublishedAt());
            }
        }

        if (articleData.getUpdatedAt() != null) {
            try {
                builder.updatedAt(LocalDateTime.parse(articleData.getUpdatedAt(), dateFormatter));
            } catch (Exception e) {
                log.debug("Failed to parse updatedAt date: {}", articleData.getUpdatedAt());
            }
        }

        return builder.build();
    }

    private ArticleData convertToArticleData(Article article) {
        ArticleData.ArticleDataBuilder builder = ArticleData.builder()
                .id(article.getId())
                .url(article.getUrl())
                .title(article.getTitle())
                .content(article.getContent())
                .sourceSite(article.getSource().getCode())
                .extractedAt(article.getExtractedAt())
                .extractionMethod(article.getExtractionMethod())
                .imageUrl(article.getImageUrl())
                .imageCaption(article.getImageCaption())
                .category(article.getCategory());

        // Convert comma-separated authors to list
        if (article.getAuthors() != null && !article.getAuthors().trim().isEmpty()) {
            List<String> authors = Stream.of(article.getAuthors().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            builder.authors(authors);
        }

        // Convert comma-separated tags to list
        if (article.getTags() != null && !article.getTags().trim().isEmpty()) {
            List<String> tags = Stream.of(article.getTags().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            builder.tags(tags);
        }

        // Format dates to strings
        if (article.getPublishedAt() != null) {
            builder.publishedAt(article.getPublishedAt().format(dateFormatter));
        }

        if (article.getUpdatedAt() != null) {
            builder.updatedAt(article.getUpdatedAt().format(dateFormatter));
        }

        return builder.build();
    }
}