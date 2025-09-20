package com.factfeed.backend.controller;

import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.db.repository.ArticleRepository;
import com.factfeed.backend.scraper.model.NewsSource;
import com.factfeed.backend.scraper.service.ArticleScrapingService;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandler;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandlerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scraping")
@RequiredArgsConstructor
@Slf4j
public class ScrapingController {

    private final UrlDiscoveryHandlerFactory urlDiscoveryFactory;
    private final ArticleScrapingService articleScrapingService;
    private final ArticleRepository articleRepository;
    private final WebDriver driver;

    @PostMapping("/full/{source}")
    public ResponseEntity<Map<String, Object>> performFullScraping(
            @PathVariable String source,
            @RequestParam(defaultValue = "20") int maxUrls) {

        try {
            NewsSource newsSource = NewsSource.valueOf(source.toUpperCase());
            LocalDateTime startTime = LocalDateTime.now();

            log.info("🚀 Starting full scraping for source: {} (max URLs: {})", newsSource.getDisplayName(), maxUrls);

            // Step 1: Discover URLs
            UrlDiscoveryHandler urlHandler = urlDiscoveryFactory.createHandler(driver, newsSource);
            List<String> discoveredUrls = urlHandler.discoveredUrls(maxUrls);

            log.info("📊 URL Discovery completed: {} URLs found for {}", discoveredUrls.size(), newsSource.getDisplayName());

            // Step 2: Scrape articles from discovered URLs
            List<Article> scrapedArticles = articleScrapingService.scrapeArticlesFromUrls(discoveredUrls, newsSource);

            LocalDateTime endTime = LocalDateTime.now();

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("sourceCode", newsSource.name());
            response.put("sourceName", newsSource.getDisplayName());
            response.put("urlsDiscovered", discoveredUrls.size());
            response.put("articlesScraped", scrapedArticles.size());
            response.put("startTime", startTime);
            response.put("endTime", endTime);
            response.put("success", true);

            // Include article details
            List<Map<String, Object>> articleSummaries = new ArrayList<>();
            for (Article article : scrapedArticles) {
                Map<String, Object> articleInfo = new HashMap<>();
                articleInfo.put("id", article.getId());
                articleInfo.put("title", article.getTitle());
                articleInfo.put("url", article.getUrl());
                articleInfo.put("publishedAt", article.getArticlePublishedAt());
                articleInfo.put("scrapedAt", article.getScrapedAt());
                articleSummaries.add(articleInfo);
            }
            response.put("articles", articleSummaries);

            log.info("✅ Full scraping completed for {}: {} URLs discovered, {} articles scraped",
                    newsSource.getDisplayName(), discoveredUrls.size(), scrapedArticles.size());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Error during full scraping for source {}: {}", source, e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("sourceCode", source);
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            errorResponse.put("urlsDiscovered", 0);
            errorResponse.put("articlesScraped", 0);

            return ResponseEntity.ok(errorResponse);
        }
    }

    @PostMapping("/full/all")
    public ResponseEntity<Map<String, Object>> performFullScrapingAllSources(
            @RequestParam(defaultValue = "20") int maxUrlsPerSource) {

        LocalDateTime startTime = LocalDateTime.now();
        Map<String, Object> overallResponse = new HashMap<>();
        List<Map<String, Object>> sourceResults = new ArrayList<>();

        int totalUrlsDiscovered = 0;
        int totalArticlesScraped = 0;
        int successfulSources = 0;
        List<String> failedSources = new ArrayList<>();

        for (NewsSource source : NewsSource.values()) {
            try {
                log.info("🔄 Processing source: {}", source.getDisplayName());

                // Step 1: Discover URLs
                UrlDiscoveryHandler urlHandler = urlDiscoveryFactory.createHandler(driver, source);
                List<String> discoveredUrls = urlHandler.discoveredUrls(maxUrlsPerSource);

                // Step 2: Scrape articles
                List<Article> scrapedArticles = articleScrapingService.scrapeArticlesFromUrls(discoveredUrls, source);

                // Track results
                totalUrlsDiscovered += discoveredUrls.size();
                totalArticlesScraped += scrapedArticles.size();
                
                boolean isSuccessful = scrapedArticles.size() > 0;
                if (isSuccessful) {
                    successfulSources++;
                    log.info("✅ {} - URLs: {}, Articles: {}", source.getDisplayName(),
                            discoveredUrls.size(), scrapedArticles.size());
                } else {
                    failedSources.add(source.getDisplayName());
                    log.warn("⚠️ {} - URLs: {}, Articles: {} (NO ARTICLES SCRAPED)", source.getDisplayName(),
                            discoveredUrls.size(), scrapedArticles.size());
                }

                // Add to results
                Map<String, Object> sourceResult = new HashMap<>();
                sourceResult.put("sourceCode", source.name());
                sourceResult.put("sourceName", source.getDisplayName());
                sourceResult.put("urlsDiscovered", discoveredUrls.size());
                sourceResult.put("articlesScraped", scrapedArticles.size());
                sourceResult.put("success", isSuccessful);
                sourceResults.add(sourceResult);

            } catch (Exception e) {
                log.error("❌ Error processing source {}: {}", source.getDisplayName(), e.getMessage());
                failedSources.add(source.getDisplayName() + " (ERROR: " + e.getMessage() + ")");

                Map<String, Object> sourceResult = new HashMap<>();
                sourceResult.put("sourceCode", source.name());
                sourceResult.put("sourceName", source.getDisplayName());
                sourceResult.put("urlsDiscovered", 0);
                sourceResult.put("articlesScraped", 0);
                sourceResult.put("success", false);
                sourceResult.put("error", e.getMessage());
                sourceResults.add(sourceResult);
            }
        }

        LocalDateTime endTime = LocalDateTime.now();

        // Overall response
        overallResponse.put("totalUrlsDiscovered", totalUrlsDiscovered);
        overallResponse.put("totalArticlesScraped", totalArticlesScraped);
        overallResponse.put("successfulSources", successfulSources);
        overallResponse.put("totalSources", NewsSource.values().length);
        overallResponse.put("maxUrlsPerSource", maxUrlsPerSource);
        overallResponse.put("startTime", startTime);
        overallResponse.put("endTime", endTime);
        overallResponse.put("sourceResults", sourceResults);

        if (failedSources.isEmpty()) {
            log.info("🎯 Full scraping completed - URLs: {}, Articles: {}, All sources successful: {}/{}",
                    totalUrlsDiscovered, totalArticlesScraped, successfulSources, NewsSource.values().length);
        } else {
            log.info("🎯 Full scraping completed - URLs: {}, Articles: {}, Successful sources: {}/{}, Failed: {}",
                    totalUrlsDiscovered, totalArticlesScraped, successfulSources, NewsSource.values().length, failedSources);
        }

        return ResponseEntity.ok(overallResponse);
    }

    @GetMapping("/article/{id}")
    public ResponseEntity<Map<String, Object>> getArticleDetails(@PathVariable Long id) {
        try {
            Optional<Article> articleOpt = articleRepository.findById(id);
            if (articleOpt.isEmpty()) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("error", "Article not found");
                return ResponseEntity.status(404).body(errorResponse);
            }

            Article article = articleOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("id", article.getId());
            response.put("title", article.getTitle());
            response.put("url", article.getUrl());
            response.put("author", article.getAuthor());
            response.put("content", article.getContent());
            response.put("summarizedContent", article.getSummarizedContent());
            response.put("category", article.getCategory());
            response.put("tags", article.getTags());
            response.put("imageUrl", article.getImageUrl());
            response.put("source", article.getSource().getDisplayName());
            response.put("publishedAt", article.getArticlePublishedAt());
            response.put("scrapedAt", article.getScrapedAt());
            response.put("contentLength", article.getContent() != null ? article.getContent().length() : 0);
            response.put("success", true);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting article details for ID {}: {}", id, e.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}