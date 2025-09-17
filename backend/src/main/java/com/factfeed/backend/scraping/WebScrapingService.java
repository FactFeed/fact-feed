package com.factfeed.backend.scraping;

import com.factfeed.backend.article.ArticleService;
import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.config.ScrapingConfig;
import com.factfeed.backend.model.dto.ArticleDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.dto.ScrapingStatusDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Main web scraping service that orchestrates URL discovery and article extraction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebScrapingService {

    private final UrlDiscoveryService urlDiscoveryService;
    private final ArticleExtractorService articleExtractorService;
    private final NewsSourceConfigService configService;
    private final ArticleService articleService;
    private final ScrapingConfig scrapingConfig;

    // Store scraping task statuses
    private final Map<String, ScrapingStatusDTO> taskStatuses = new ConcurrentHashMap<>();

    /**
     * Scrape articles from all configured news sources
     */
    public ScrapingResultDTO scrapeAllSources(int limitPerSource) {
        log.info("Starting scraping for all sources with limit {} per source", limitPerSource);

        List<ArticleDTO> allArticles = new ArrayList<>();
        int totalRequested = 0;
        int totalFound = 0;
        long startTime = System.currentTimeMillis();

        List<NewsSource> sources = new ArrayList<>();
        for (NewsSource s : NewsSource.values()) {
            if (configService.hasConfig(s)) sources.add(s);
        }

        // Scrape sources in parallel to reduce overall runtime
        List<ScrapingResultDTO> results = sources.parallelStream().map(source -> {
            try {
                return scrapeSource(source, limitPerSource);
            } catch (Exception e) {
                log.error("Error scraping source {}: {}", source, e.getMessage());
                return createEmptyResult(source.name(), 0);
            }
        }).toList();

        for (ScrapingResultDTO r : results) {
            allArticles.addAll(r.getArticles());
            totalRequested += r.getTotalRequested();
            totalFound += r.getTotalFound();
        }

        double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double successRate = totalRequested > 0 ? (double) allArticles.size() / totalRequested * 100 : 0;

        ScrapingResultDTO result = new ScrapingResultDTO();
        result.setArticles(allArticles);
        result.setSiteName("All Sources");
        result.setTotalRequested(totalRequested);
        result.setTotalFound(totalFound);
        result.setTotalValid(allArticles.size());
        result.setScrapedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.setDurationSeconds(durationSeconds);
        result.setSuccessRate(successRate);

        log.info("Completed scraping all sources: {} articles from {} total requested",
                allArticles.size(), totalRequested);

        return result;
    }

    /**
     * Scrape articles from a specific news source
     */
    public ScrapingResultDTO scrapeSource(NewsSource source, int limit) {
        log.info("Starting scraping for {} with limit {}", source, limit);

        long startTime = System.currentTimeMillis();
        List<ArticleDTO> articles = new ArrayList<>();

        NewsSourceConfig config = configService.getConfig(source);
        if (config == null) {
            log.error("No configuration found for source: {}", source);
            return createEmptyResult(source.name(), 0);
        }

        try {
            // Discover URLs
            List<String> urls = urlDiscoveryService.discoverUrls(source, limit);
            log.info("Discovered {} URLs for {}", urls.size(), source);

            // Extract articles from URLs
            if (scrapingConfig.isParallelPerSourceEnabled() && scrapingConfig.getMaxConcurrentPerSource() > 1) {
                int parallelism = Math.max(1, scrapingConfig.getMaxConcurrentPerSource());
                articles = urls.parallelStream()
                        .limit(limit)
                        .map(url -> {
                            try {
                                return articleExtractorService.extractArticle(url, config);
                            } catch (Exception e) {
                                log.error("Error extracting article from URL {}: {}", url, e.getMessage());
                                return null;
                            }
                        })
                        .filter(a -> a != null)
                        .toList();
            } else {
                int processed = 0;
                for (String url : urls) {
                    try {
                        ArticleDTO article = articleExtractorService.extractArticle(url, config);
                        if (article != null) {
                            articles.add(article);
                            log.debug("Successfully extracted article: {}", article.getTitle());
                        }

                        processed++;
                        if (processed < urls.size()) {
                            Thread.sleep((long) (scrapingConfig.getDefaultDelay() * 1000));
                        }
                    } catch (Exception e) {
                        log.error("Error extracting article from URL {}: {}", url, e.getMessage());
                    }
                }
            }

            double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
            double successRate = urls.size() > 0 ? (double) articles.size() / urls.size() * 100 : 0;

            ScrapingResultDTO result = new ScrapingResultDTO();
            result.setArticles(articles);
            result.setSiteName(source.name());
            result.setTotalRequested(urls.size());
            result.setTotalFound(urls.size());
            result.setTotalValid(articles.size());
            result.setScrapedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            result.setDurationSeconds(durationSeconds);
            result.setSuccessRate(successRate);

            log.info("Completed scraping for {}: {} valid articles from {} URLs in {:.2f}s",
                    source, articles.size(), urls.size(), durationSeconds);

            return result;

        } catch (Exception e) {
            log.error("Error during scraping for source {}: {}", source, e.getMessage());
            return createEmptyResult(source.name(), 0);
        }
    }

    /**
     * Scrape specific URLs
     */
    public ScrapingResultDTO scrapeUrls(List<String> urls, NewsSource source) {
        log.info("Scraping {} specific URLs for {}", urls.size(), source);

        long startTime = System.currentTimeMillis();
        List<ArticleDTO> articles = new ArrayList<>();

        NewsSourceConfig config = configService.getConfig(source);
        if (config == null) {
            log.error("No configuration found for source: {}", source);
            return createEmptyResult(source.name(), urls.size());
        }

        for (String url : urls) {
            try {
                ArticleDTO article = articleExtractorService.extractArticle(url, config);
                if (article != null) {
                    articles.add(article);
                }

                // Add delay between requests
                Thread.sleep((long) (scrapingConfig.getDefaultDelay() * 1000));

            } catch (Exception e) {
                log.error("Error extracting article from URL {}: {}", url, e.getMessage());
            }
        }

        double durationSeconds = (System.currentTimeMillis() - startTime) / 1000.0;
        double successRate = urls.size() > 0 ? (double) articles.size() / urls.size() * 100 : 0;

        ScrapingResultDTO result = new ScrapingResultDTO();
        result.setArticles(articles);
        result.setSiteName(source.name());
        result.setTotalRequested(urls.size());
        result.setTotalFound(urls.size());
        result.setTotalValid(articles.size());
        result.setScrapedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.setDurationSeconds(durationSeconds);
        result.setSuccessRate(successRate);

        return result;
    }

    /**
     * Scrape and save articles asynchronously
     */
    @Async("scrapingTaskExecutor")
    public CompletableFuture<ScrapingResultDTO> scrapeAndSaveAsync(NewsSource source, int limit) {
        String taskId = generateTaskId(source);

        // Initialize task status
        ScrapingStatusDTO status = new ScrapingStatusDTO();
        status.setTaskId(taskId);
        status.setStatus("RUNNING");
        status.setSourceName(source.name());
        status.setTotalUrls(limit);
        status.setStartedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        taskStatuses.put(taskId, status);

        try {
            log.info("Starting async scraping task {} for {}", taskId, source);

            ScrapingResultDTO result = scrapeSource(source, limit);

            // Save articles to database
            List<Article> savedArticles = articleService.processScrapingResult(result);

            // Update task status
            status.setStatus("COMPLETED");
            status.setSuccessfulUrls(savedArticles.size());
            status.setFailedUrls(result.getTotalRequested() - savedArticles.size());
            status.setCompletedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            status.setDurationSeconds(result.getDurationSeconds());
            status.setProgressPercentage(100.0);

            log.info("Completed async scraping task {} for {}: {} articles saved",
                    taskId, source, savedArticles.size());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Error in async scraping task {} for {}: {}", taskId, source, e.getMessage());

            status.setStatus("FAILED");
            status.setError(e.getMessage());
            status.setCompletedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            throw e;
        }
    }

    /**
     * Scrape and save all sources asynchronously
     */
    @Async("scrapingTaskExecutor")
    public CompletableFuture<ScrapingResultDTO> scrapeAndSaveAllAsync(int limitPerSource) {
        String taskId = "all-sources-" + System.currentTimeMillis();

        ScrapingStatusDTO status = new ScrapingStatusDTO();
        status.setTaskId(taskId);
        status.setStatus("RUNNING");
        status.setSourceName("All Sources");
        status.setStartedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        taskStatuses.put(taskId, status);

        try {
            ScrapingResultDTO result = scrapeAllSources(limitPerSource);

            // Save articles to database
            List<Article> savedArticles = articleService.processScrapingResult(result);

            status.setStatus("COMPLETED");
            status.setSuccessfulUrls(savedArticles.size());
            status.setCompletedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            status.setDurationSeconds(result.getDurationSeconds());
            status.setProgressPercentage(100.0);

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            status.setStatus("FAILED");
            status.setError(e.getMessage());
            status.setCompletedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            throw e;
        }
    }

    /**
     * Get status of a scraping task
     */
    public ScrapingStatusDTO getTaskStatus(String taskId) {
        return taskStatuses.get(taskId);
    }

    /**
     * Get all task statuses
     */
    public Map<String, ScrapingStatusDTO> getAllTaskStatuses() {
        return new ConcurrentHashMap<>(taskStatuses);
    }

    private ScrapingResultDTO createEmptyResult(String siteName, int totalRequested) {
        ScrapingResultDTO result = new ScrapingResultDTO();
        result.setArticles(new ArrayList<>());
        result.setSiteName(siteName);
        result.setTotalRequested(totalRequested);
        result.setTotalFound(0);
        result.setTotalValid(0);
        result.setScrapedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        result.setDurationSeconds(0.0);
        result.setSuccessRate(0.0);
        return result;
    }

    private String generateTaskId(NewsSource source) {
        return source.name().toLowerCase() + "-" + System.currentTimeMillis();
    }
}