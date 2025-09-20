package com.factfeed.backend.scraper.service;

import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.db.repository.ArticleRepository;
import com.factfeed.backend.scraper.config.ArticleExtractor;
import com.factfeed.backend.scraper.model.NewsSource;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ArticleScrapingService {

    private final ArticleRepository articleRepository;
    private final ArticleExtractor articleExtractor;
    private final ExecutorService executorService;

    public ArticleScrapingService(
            ArticleRepository articleRepository,
            ArticleExtractor articleExtractor,
            @Value("${article.scraping.thread-pool-size:10}") int threadPoolSize) {
        this.articleRepository = articleRepository;
        this.articleExtractor = articleExtractor;
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        log.info("📊 Initialized article scraping service with thread pool size: {}", threadPoolSize);
    }

    @Transactional
    public List<Article> scrapeArticlesFromUrls(List<String> urls, NewsSource source) {
        log.info("🚀 Starting to scrape {} URLs for source: {}", urls.size(), source.getDisplayName());

        // Filter out URLs already in database
        List<String> existingUrls = articleRepository.findExistingUrls(urls);
        List<String> newUrls = urls.stream()
                .filter(url -> !existingUrls.contains(url))
                .collect(Collectors.toList());

        log.info("📊 Filtered URLs - Total: {}, Already in DB: {}, New to scrape: {}",
                urls.size(), existingUrls.size(), newUrls.size());

        if (newUrls.isEmpty()) {
            log.info("✅ No new articles to scrape for {}", source.getDisplayName());
            return new ArrayList<>();
        }

        // Scrape articles asynchronously
        List<CompletableFuture<Article>> futures = newUrls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> scrapeArticle(url, source), executorService))
                .collect(Collectors.toList());

        // Wait for all scraping to complete
        List<Article> scrapedArticles = futures.stream()
                .map(CompletableFuture::join)
                .filter(article -> article != null) // Filter out failed scrapes
                .collect(Collectors.toList());

        // Save all successful articles to database
        if (!scrapedArticles.isEmpty()) {
            List<Article> savedArticles = articleRepository.saveAll(scrapedArticles);
            log.info("✅ Successfully scraped and saved {} articles for {}",
                    savedArticles.size(), source.getDisplayName());
            return savedArticles;
        }

        log.warn("⚠️ No articles were successfully scraped for {}", source.getDisplayName());
        return new ArrayList<>();
    }

    private Article scrapeArticle(String url, NewsSource source) {
        try {
            log.debug("🔍 Scraping article: {}", url);

            // Extract article content using JSON-LD
            ArticleExtractor.ExtractedArticle extracted = articleExtractor.extractArticle(url, source);

            if (extracted == null || extracted.getTitle() == null || extracted.getContent() == null) {
                log.warn("❌ Failed to extract content from: {}", url);
                return null;
            }

            // Create Article entity
            Article article = Article.builder()
                    .url(url)
                    .source(source)
                    .title(extracted.getTitle().trim())
                    .author(extracted.getAuthor())
                    .authorLocation(extracted.getAuthorLocation())
                    .content(extracted.getContent().trim())
                    .summarizedContent(null) // Will be filled by summarization service later
                    .category(extracted.getCategory())
                    .tags(extracted.getTags())
                    .imageUrl(extracted.getImageUrl())
                    .imageCaption(extracted.getImageCaption())
                    .articlePublishedAt(extracted.getArticlePublishedAt() != null ?
                            extracted.getArticlePublishedAt() : LocalDateTime.now())
                    .articleUpdatedAt(extracted.getArticleUpdatedAt())
                    .build();

            log.debug("✅ Successfully extracted: {} - {}", article.getTitle(), url);
            return article;

        } catch (Exception e) {
            log.error("❌ Error scraping article {}: {}", url, e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("🔄 Shutting down article scraping executor service...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("⚠️ Executor did not terminate gracefully, forcing shutdown...");
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("❌ Executor did not terminate even after force shutdown");
                }
            } else {
                log.info("✅ Article scraping executor service shut down gracefully");
            }
        } catch (InterruptedException e) {
            log.warn("⚠️ Shutdown interrupted, forcing immediate shutdown...");
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}