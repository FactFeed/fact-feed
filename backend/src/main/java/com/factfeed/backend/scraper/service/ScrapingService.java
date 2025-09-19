package com.factfeed.backend.scraper.service;

import com.factfeed.backend.database.service.ArticleService;
import com.factfeed.backend.scraper.config.SiteConfig;
import com.factfeed.backend.scraper.discovery.UrlDiscoveryHandler;
import com.factfeed.backend.scraper.extraction.ArticleExtractor;
import com.factfeed.backend.scraper.model.ArticleData;
import com.factfeed.backend.scraper.model.ScrapingResult;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * Main scraping orchestration service
 * Coordinates URL discovery, article extraction, and storage
 */
@Service
@Slf4j
public class ScrapingService {

    private final ApplicationContext applicationContext;
    private final ArticleExtractor articleExtractor;
    private final ArticleService articleService;
    // Tier 1 sites with complete JSON-LD support
    private final List<SiteConfig> tier1Sites = Arrays.asList(
            new SiteConfig("samakal", "https://samakal.com", true),
            new SiteConfig("prothom_alo", "https://prothomalo.com", true),
            new SiteConfig("jugantor", "https://jugantor.com", true),
            new SiteConfig("ittefaq", "https://ittefaq.com.bd", true)
    );
    private ExecutorService executorService;
    @Value("${scraper.batch.size:20}")
    private int batchSize;
    @Value("${scraper.concurrent.threads:3}")
    private int concurrentThreads;
    @Value("${scraper.discovery.max-urls:100}")
    private int maxUrlsPerSite;

    @Autowired
    public ScrapingService(ApplicationContext applicationContext,
                           ArticleExtractor articleExtractor,
                           ArticleService articleService) {
        this.applicationContext = applicationContext;
        this.articleExtractor = articleExtractor;
        this.articleService = articleService;
    }

    @PostConstruct
    public void initialize() {
        this.executorService = Executors.newFixedThreadPool(concurrentThreads);
        log.info("ScrapingService initialized with {} threads", concurrentThreads);
    }

    /**
     * Scrape all Tier 1 sites for latest articles
     */
    public ScrapingResult scrapeAllSites() {
        log.info("Starting comprehensive scraping of all Tier 1 sites");

        List<CompletableFuture<ScrapingResult>> futures = tier1Sites.stream()
                .filter(SiteConfig::isActive)
                .map(site -> CompletableFuture.supplyAsync(() -> scrapeSite(site), executorService))
                .toList();

        // Wait for all sites to complete
        List<ScrapingResult> siteResults = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // Combine results
        ScrapingResult combinedResult = combineResults(siteResults);

        log.info("Comprehensive scraping completed. Total articles: {}, Success rate: {}%",
                combinedResult.getTotalArticles(),
                String.format("%.1f", combinedResult.getSuccessRate() * 100));

        return combinedResult;
    }

    /**
     * Scrape a specific site
     */
    public ScrapingResult scrapeSite(String siteName) {
        SiteConfig siteConfig = tier1Sites.stream()
                .filter(site -> site.getSiteName().equals(siteName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown site: " + siteName));

        return scrapeSite(siteConfig);
    }

    /**
     * Scrape a specific site using its configuration
     */
    private ScrapingResult scrapeSite(SiteConfig siteConfig) {
        log.info("Starting scraping for site: {}", siteConfig.getSiteName());

        WebDriver driver = null;
        ScrapingResult result = new ScrapingResult();
        result.setSiteName(siteConfig.getSiteName());
        result.setStartTime(LocalDateTime.now());

        try {
            // Get WebDriver instance
            driver = applicationContext.getBean(WebDriver.class);

            // Get the appropriate URL discovery handler
            UrlDiscoveryHandler discoveryHandler = getDiscoveryHandler(siteConfig.getSiteName());

            // Load existing articles to avoid duplicates
            Set<String> existingUrls = articleService.getExistingUrls(siteConfig.getSiteName());
            log.info("Found {} existing articles for {}", existingUrls.size(), siteConfig.getSiteName());

            // Discover URLs
            log.info("Discovering URLs for {}", siteConfig.getSiteName());
            List<String> discoveredUrls = discoveryHandler.discoverUrls(
                    driver,
                    siteConfig.getBaseUrl(),
                    maxUrlsPerSite,
                    existingUrls
            );

            log.info("Discovered {} new URLs for {}", discoveredUrls.size(), siteConfig.getSiteName());
            result.setDiscoveredUrls(discoveredUrls.size());

            if (discoveredUrls.isEmpty()) {
                log.info("No new URLs found for {}", siteConfig.getSiteName());
                result.setEndTime(LocalDateTime.now());
                return result;
            }

            // Extract articles in batches
            List<ArticleData> extractedArticles = new ArrayList<>();
            int processed = 0;

            for (int i = 0; i < discoveredUrls.size(); i += batchSize) {
                int endIndex = Math.min(i + batchSize, discoveredUrls.size());
                List<String> batch = discoveredUrls.subList(i, endIndex);

                log.info("Processing batch {}/{} for {} ({} URLs)",
                        (i / batchSize) + 1,
                        (discoveredUrls.size() + batchSize - 1) / batchSize,
                        siteConfig.getSiteName(),
                        batch.size());

                List<ArticleData> batchResults = extractBatch(batch, siteConfig.getSiteName());
                extractedArticles.addAll(batchResults);
                processed += batch.size();

                result.setProcessedUrls(processed);

                // Small delay between batches to be respectful
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // Validate and store articles
            List<ArticleData> validArticles = extractedArticles.stream()
                    .filter(article -> article.isValid().isValid())
                    .collect(Collectors.toList());

            log.info("Validated {} out of {} extracted articles for {}",
                    validArticles.size(), extractedArticles.size(), siteConfig.getSiteName());

            // Store articles
            if (!validArticles.isEmpty()) {
                articleService.storeArticles(validArticles, siteConfig.getSiteName());
                log.info("Stored {} articles for {}", validArticles.size(), siteConfig.getSiteName());
            }

            result.setSuccessfulExtractions(validArticles.size());
            result.setFailedExtractions(extractedArticles.size() - validArticles.size());
            result.setExtractedArticles(extractedArticles);

        } catch (Exception e) {
            log.error("Error scraping site {}: {}", siteConfig.getSiteName(), e.getMessage(), e);
            result.addError("Scraping failed: " + e.getMessage());
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    log.warn("Error closing WebDriver: {}", e.getMessage());
                }
            }
            result.setEndTime(LocalDateTime.now());
        }

        log.info("Scraping completed for {}. Success rate: {}%",
                siteConfig.getSiteName(),
                String.format("%.1f", result.getSuccessRate() * 100));

        return result;
    }

    /**
     * Extract articles from a batch of URLs
     */
    private List<ArticleData> extractBatch(List<String> urls, String siteName) {
        List<CompletableFuture<ArticleData>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return articleExtractor.extractArticle(url, siteName);
                    } catch (Exception e) {
                        log.warn("Failed to extract article from {}: {}", url, e.getMessage());
                        return null;
                    }
                }, executorService))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get the appropriate URL discovery handler for a site
     */
    private UrlDiscoveryHandler getDiscoveryHandler(String siteName) {
        String beanName = siteName.toLowerCase() + "UrlDiscovery";
        try {
            return applicationContext.getBean(beanName, UrlDiscoveryHandler.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("No discovery handler found for site: " + siteName);
        }
    }

    /**
     * Combine results from multiple sites
     */
    private ScrapingResult combineResults(List<ScrapingResult> siteResults) {
        ScrapingResult combined = new ScrapingResult();
        combined.setSiteName("ALL_SITES");
        combined.setStartTime(siteResults.stream()
                .map(ScrapingResult::getStartTime)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now()));
        combined.setEndTime(LocalDateTime.now());

        int totalDiscovered = siteResults.stream().mapToInt(ScrapingResult::getDiscoveredUrls).sum();
        int totalProcessed = siteResults.stream().mapToInt(ScrapingResult::getProcessedUrls).sum();
        int totalSuccessful = siteResults.stream().mapToInt(ScrapingResult::getSuccessfulExtractions).sum();
        int totalFailed = siteResults.stream().mapToInt(ScrapingResult::getFailedExtractions).sum();

        combined.setDiscoveredUrls(totalDiscovered);
        combined.setProcessedUrls(totalProcessed);
        combined.setSuccessfulExtractions(totalSuccessful);
        combined.setFailedExtractions(totalFailed);

        // Collect all articles
        List<ArticleData> allArticles = siteResults.stream()
                .flatMap(result -> result.getExtractedArticles().stream())
                .collect(Collectors.toList());
        combined.setExtractedArticles(allArticles);

        // Collect all errors
        List<String> allErrors = siteResults.stream()
                .flatMap(result -> result.getErrors().stream())
                .collect(Collectors.toList());
        combined.setErrors(allErrors);

        return combined;
    }

    /**
     * Get scraping statistics
     */
    public Map<String, Object> getScrapingStats() {
        Map<String, Object> stats = new HashMap<>();

        for (SiteConfig site : tier1Sites) {
            if (site.isActive()) {
                try {
                    int articleCount = articleService.getArticleCount(site.getSiteName());
                    LocalDateTime lastScrapeTime = articleService.getLastScrapeTime(site.getSiteName());

                    Map<String, Object> siteStats = new HashMap<>();
                    siteStats.put("articleCount", articleCount);
                    siteStats.put("lastScrapeTime", lastScrapeTime);
                    siteStats.put("isActive", site.isActive());

                    stats.put(site.getSiteName(), siteStats);
                } catch (Exception e) {
                    log.warn("Failed to get stats for {}: {}", site.getSiteName(), e.getMessage());
                }
            }
        }

        return stats;
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        log.info("Shutting down scraping service");
        executorService.shutdown();
    }
}