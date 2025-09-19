package com.factfeed.backend.scheduler;

import com.factfeed.backend.article.ArticleService;
import com.factfeed.backend.scraping.WebScrapingService;
import com.factfeed.backend.service.ApiUsageService;
import com.factfeed.backend.service.EventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks for automated news processing pipeline
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NewsProcessingScheduler {

    private final WebScrapingService webScrapingService;
    private final ArticleService articleService;
    private final EventService eventService;
    private final ApiUsageService apiUsageService;

    @Value("${scheduler.scraping.limit:20}")
    private int scrapingLimit;

    @Value("${scheduler.summarization.limit:20}")
    private int summarizationLimit;

    @Value("${scheduler.clustering.hours:6}")
    private int clusteringHours;

    @Value("${scheduler.clustering.similarity:0.7}")
    private double clusteringSimilarity;

    @Value("${scheduler.aggregation.max-age-hours:12}")
    private int aggregationMaxAgeHours;

    /**
     * Scheduled scraping task - runs every 2 hours
     */
    @Scheduled(fixedRate = 7200000) // 2 hours in milliseconds
    public void scheduledScraping() {
        log.info("Starting scheduled scraping task with limit: {}", scrapingLimit);

        try {
            // Check if we have API quota available
            if (!apiUsageService.isRequestAllowed("gemini", "primary")) {
                log.warn("API quota exceeded, skipping scheduled scraping");
                return;
            }

            // Scrape all sources with configured limit
            webScrapingService.scrapeAndSaveAllAsync(scrapingLimit);
            log.info("Scheduled scraping task initiated successfully with limit: {}", scrapingLimit);

        } catch (Exception e) {
            log.error("Error in scheduled scraping task: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled summarization task - runs every 30 minutes
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes in milliseconds
    public void scheduledSummarization() {
        log.info("Starting scheduled summarization task with limit: {}", summarizationLimit);

        try {
            // Check if we have API quota available for summarization
            if (!apiUsageService.isRequestAllowed("gemini", "primary")) {
                log.warn("API quota exceeded, skipping scheduled summarization");
                return;
            }

            // Summarize unsummarized articles
            var results = articleService.summarizeUnsummarizedArticles(summarizationLimit);
            long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();

            log.info("Scheduled summarization task completed: {}/{} articles summarized successfully",
                    successful, results.size());

        } catch (Exception e) {
            log.error("Error in scheduled summarization task: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled event clustering task - runs every hour
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void scheduledEventClustering() {
        log.info("Starting scheduled event clustering task for last {} hours with similarity threshold: {}",
                clusteringHours, clusteringSimilarity);

        try {
            // Check if we have API quota available for clustering
            if (!apiUsageService.isRequestAllowed("gemini", "primary")) {
                log.warn("API quota exceeded, skipping scheduled event clustering");
                return;
            }

            // Process articles for event clustering
            var result = eventService.processRecentArticlesForEvents(clusteringHours, clusteringSimilarity);

            if (result.isSuccess()) {
                log.info("Scheduled event clustering task completed: {} articles processed, {} events created",
                        result.getTotalArticles(), result.getClusters().size());
            } else {
                log.error("Scheduled event clustering failed: {}", result.getError());
            }

        } catch (Exception e) {
            log.error("Error in scheduled event clustering task: {}", e.getMessage(), e);
        }
    }

    /**
     * Scheduled aggregation update task - runs every 3 hours
     */
    @Scheduled(fixedRate = 10800000) // 3 hours in milliseconds
    public void scheduledAggregationUpdate() {
        log.info("Starting scheduled aggregation update task for events older than {} hours",
                aggregationMaxAgeHours);

        try {
            // Check if we have API quota available for aggregation
            if (!apiUsageService.isRequestAllowed("gemini", "primary")) {
                log.warn("API quota exceeded, skipping scheduled aggregation update");
                return;
            }

            // Update aggregated content for events
            eventService.updateAggregatedContentForEvents(aggregationMaxAgeHours);
            log.info("Scheduled aggregation update task completed");

        } catch (Exception e) {
            log.error("Error in scheduled aggregation update task: {}", e.getMessage(), e);
        }
    }

    /**
     * Daily cleanup task - runs every day at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyCleanupTask() {
        log.info("Starting daily cleanup task");

        try {
            // Clean up old API usage logs (keep 30 days)
            apiUsageService.cleanupOldUsageLogs(30);

            // You can add more cleanup operations here:
            // - Remove old temporary files
            // - Archive old events
            // - Clean up failed scraping attempts

            log.info("Daily cleanup task completed");

        } catch (Exception e) {
            log.error("Error in daily cleanup task: {}", e.getMessage(), e);
        }
    }

    /**
     * API quota monitoring task - runs every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes in milliseconds
    public void apiQuotaMonitoring() {
        try {
            // Check current API usage and log warnings if approaching limits
            var usageStats = apiUsageService.getCurrentUsageStats("gemini", "primary");

            // Log warning if approaching 80% of limits
            if (usageStats.getRequestCount() > 800) { // 80% of 1000 limit
                log.warn("Approaching API request limit: {} requests used in last hour",
                        usageStats.getRequestCount());
            }

            if (usageStats.getTokenCount() > 80000) { // 80% of 100,000 limit
                log.warn("Approaching API token limit: {} tokens used in last hour",
                        usageStats.getTokenCount());
            }

            // Check if we should switch to a backup account
            if (apiUsageService.shouldSwitchAccount("gemini", "primary")) {
                String bestAccount = apiUsageService.selectBestAccount("gemini");
                log.info("Switching to best available account: {}", bestAccount);
                // Note: You'd need to implement account switching logic
            }

        } catch (Exception e) {
            log.error("Error in API quota monitoring task: {}", e.getMessage(), e);
        }
    }
}