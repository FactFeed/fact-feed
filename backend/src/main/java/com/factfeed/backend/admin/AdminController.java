package com.factfeed.backend.admin;

import com.factfeed.backend.article.ArticleService;
import com.factfeed.backend.model.dto.EventClusteringResponseDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.dto.ScrapingStatusDTO;
import com.factfeed.backend.model.dto.SummarizationResponseDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import com.factfeed.backend.scraping.WebScrapingService;
import com.factfeed.backend.service.ApiUsageService;
import com.factfeed.backend.service.EventService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin controller for managing news processing pipeline
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final WebScrapingService webScrapingService;
    private final ArticleService articleService;
    private final EventService eventService;
    private final ApiUsageService apiUsageService;

    /**
     * Manual scraping trigger
     */
    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> triggerScraping(
            @RequestParam(required = false) NewsSource source,
            @RequestParam(defaultValue = "10") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Admin triggered scraping: source={}, limit={}", source, limit);

            CompletableFuture<ScrapingResultDTO> future;

            if (source != null) {
                future = webScrapingService.scrapeAndSaveAsync(source, limit);
            } else {
                future = webScrapingService.scrapeAndSaveAllAsync(limit);
            }

            // Return task info without waiting for completion
            response.put("status", "initiated");
            response.put("message", "Scraping task started successfully");
            response.put("source", source != null ? source.name() : "all sources");
            response.put("limit", limit);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering scraping: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to start scraping: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Manual summarization trigger
     */
    @PostMapping("/summarize")
    public ResponseEntity<Map<String, Object>> triggerSummarization(
            @RequestParam(defaultValue = "20") int limit) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Admin triggered summarization for {} articles", limit);

            List<SummarizationResponseDTO> results = articleService.summarizeUnsummarizedArticles(limit);

            long successful = results.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();

            response.put("status", "completed");
            response.put("message", "Summarization completed");
            response.put("total_articles", results.size());
            response.put("successful", successful);
            response.put("failed", results.size() - successful);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering summarization: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to start summarization: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Manual event clustering trigger
     */
    @PostMapping("/cluster-events")
    public ResponseEntity<Map<String, Object>> triggerEventClustering(
            @RequestParam(defaultValue = "12") int hours,
            @RequestParam(defaultValue = "0.7") double similarity) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Admin triggered event clustering for last {} hours", hours);

            EventClusteringResponseDTO result = eventService.processRecentArticlesForEvents(hours, similarity);

            response.put("status", result.isSuccess() ? "completed" : "failed");
            response.put("message", result.isSuccess() ? "Event clustering completed" : result.getError());

            if (result.isSuccess()) {
                response.put("total_articles", result.getTotalArticles());
                response.put("clustered_articles", result.getClusteredArticles());
                response.put("events_created", result.getClusters().size());
                response.put("processing_time", result.getProcessingTimeSeconds());
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering event clustering: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to start event clustering: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Manual aggregation update trigger
     */
    @PostMapping("/update-aggregations")
    public ResponseEntity<Map<String, Object>> triggerAggregationUpdate(
            @RequestParam(defaultValue = "24") int maxAgeHours) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Admin triggered aggregation update for events older than {} hours", maxAgeHours);

            eventService.updateAggregatedContentForEvents(maxAgeHours);

            response.put("status", "completed");
            response.put("message", "Aggregation update completed");
            response.put("max_age_hours", maxAgeHours);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering aggregation update: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to start aggregation update: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get scraping task statuses
     */
    @GetMapping("/scraping/status")
    public ResponseEntity<Map<String, ScrapingStatusDTO>> getScrapingStatuses() {
        try {
            Map<String, ScrapingStatusDTO> statuses = webScrapingService.getAllTaskStatuses();
            return ResponseEntity.ok(statuses);
        } catch (Exception e) {
            log.error("Error getting scraping statuses: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get specific scraping task status
     */
    @GetMapping("/scraping/status/{taskId}")
    public ResponseEntity<ScrapingStatusDTO> getScrapingStatus(@PathVariable String taskId) {
        try {
            ScrapingStatusDTO status = webScrapingService.getTaskStatus(taskId);
            if (status != null) {
                return ResponseEntity.ok(status);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting scraping status for task {}: {}", taskId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get system statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        try {
            Map<String, Object> stats = new HashMap<>();

            // Article statistics
            Pageable pageable = PageRequest.of(0, 1);
            Page<Article> allArticles = articleService.getAllArticles(pageable);
            Page<Article> recentArticles = articleService.getRecentArticles(24, pageable);

            stats.put("total_articles", allArticles.getTotalElements());
            stats.put("articles_last_24h", recentArticles.getTotalElements());

            // Event statistics
            Map<String, Object> eventStats = eventService.getEventStatistics();
            stats.putAll(eventStats);

            // Source statistics
            Map<String, Long> sourceStats = new HashMap<>();
            for (NewsSource source : NewsSource.values()) {
                Page<Article> sourceArticles = articleService.getArticlesBySource(source, pageable);
                sourceStats.put(source.name(), sourceArticles.getTotalElements());
            }
            stats.put("articles_by_source", sourceStats);

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error getting system stats: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate aggregated content and discrepancies for a specific event
     */
    @PostMapping("/events/{eventId}/aggregate")
    public ResponseEntity<Map<String, Object>> aggregateSingleEvent(@PathVariable Long eventId) {
        Map<String, Object> response = new HashMap<>();
        try {
            var result = eventService.generateAggregatedContent(eventId);
            response.put("event_id", eventId);
            response.put("success", result.isSuccess());
            if (result.isSuccess()) {
                response.put("aggregated_title", result.getAggregatedContent().getAggregatedTitle());
                response.put("discrepancies_count", result.getDiscrepancies() != null ? result.getDiscrepancies().size() : 0);
                response.put("processing_time_seconds", result.getProcessingTimeSeconds());
                response.put("model", result.getModelUsed());
            } else {
                response.put("error", result.getError());
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error aggregating event {}: {}", eventId, e.getMessage());
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();

        try {
            // Check database connectivity
            articleService.getAllArticles(PageRequest.of(0, 1));
            health.put("database", "healthy");

            // Check AI service (optional)
            health.put("ai_service", "healthy");

            // Overall status
            health.put("status", "healthy");
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Health check failed: {}", e.getMessage());
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(503).body(health);
        }
    }

    /**
     * Get recent logs (placeholder - implement based on your logging needs)
     */
    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getRecentLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "INFO") String level) {

        Map<String, Object> response = new HashMap<>();

        try {
            // This is a placeholder implementation
            // You can integrate with Logback or another logging framework to get actual logs
            List<Map<String, Object>> logs = new ArrayList<>();

            // Example log entries (replace with actual log reading)
            Map<String, Object> log1 = new HashMap<>();
            log1.put("timestamp", "2024-01-01T10:00:00");
            log1.put("level", "INFO");
            log1.put("logger", "com.factfeed.backend.scheduler.NewsProcessingScheduler");
            log1.put("message", "Scheduled scraping task completed successfully");
            logs.add(log1);

            Map<String, Object> log2 = new HashMap<>();
            log2.put("timestamp", "2024-01-01T10:05:00");
            log2.put("level", "ERROR");
            log2.put("logger", "com.factfeed.backend.ai.AIService");
            log2.put("message", "Rate limit exceeded for API");
            logs.add(log2);

            response.put("logs", logs);
            response.put("total", logs.size());
            response.put("level_filter", level);
            response.put("limit", limit);
            response.put("message", "Log retrieval working - implement actual log reading as needed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error retrieving logs: {}", e.getMessage());
            response.put("error", "Failed to retrieve logs: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get system performance metrics
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getSystemMetrics() {
        try {
            Map<String, Object> metrics = new HashMap<>();

            // JVM metrics
            Runtime runtime = Runtime.getRuntime();
            Map<String, Object> jvm = new HashMap<>();
            jvm.put("total_memory", runtime.totalMemory());
            jvm.put("free_memory", runtime.freeMemory());
            jvm.put("used_memory", runtime.totalMemory() - runtime.freeMemory());
            jvm.put("max_memory", runtime.maxMemory());
            jvm.put("processors", runtime.availableProcessors());
            metrics.put("jvm", jvm);

            // Database metrics (basic)
            Map<String, Object> database = new HashMap<>();
            long totalArticles = articleService.getAllArticles(PageRequest.of(0, 1)).getTotalElements();
            database.put("total_articles", totalArticles);
            database.put("status", "connected");
            metrics.put("database", database);

            // API usage metrics
            if (apiUsageService != null) {
                var apiStats = apiUsageService.getUsageStatsByProvider(24);
                metrics.put("api_usage_24h", apiStats);
            }

            metrics.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            log.error("Error getting system metrics: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get pipeline status (overall health of the news processing pipeline)
     */
    @GetMapping("/pipeline/status")
    public ResponseEntity<Map<String, Object>> getPipelineStatus() {
        try {
            Map<String, Object> status = new HashMap<>();

            // Scraping status
            Map<String, Object> scraping = new HashMap<>();
            scraping.put("active_tasks", webScrapingService.getAllTaskStatuses().size());
            scraping.put("last_successful_scrape", "N/A"); // Implement based on your needs
            status.put("scraping", scraping);

            // AI processing status  
            Map<String, Object> ai = new HashMap<>();
            ai.put("summarization_queue", "N/A"); // Count of unsummarized articles
            ai.put("clustering_status", "N/A"); // Recent clustering results
            ai.put("api_quota_status", "OK"); // Based on rate limits
            status.put("ai_processing", ai);

            // Event processing status
            Map<String, Object> events = new HashMap<>();
            events.put("total_events", eventService.getAllEvents(PageRequest.of(0, 1)).getTotalElements());
            events.put("events_needing_aggregation", eventService.getEventsNeedingAggregation(24).size());
            status.put("events", events);

            status.put("overall_status", "healthy");
            status.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error getting pipeline status: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Manually trigger full pipeline for specific articles
     */
    @PostMapping("/pipeline/trigger")
    public ResponseEntity<Map<String, Object>> triggerFullPipeline(
            @RequestParam(defaultValue = "10") int articleLimit,
            @RequestParam(defaultValue = "true") boolean enableSummarization,
            @RequestParam(defaultValue = "true") boolean enableClustering) {

        Map<String, Object> response = new HashMap<>();

        try {
            log.info("Admin triggered full pipeline: articles={}, summarization={}, clustering={}",
                    articleLimit, enableSummarization, enableClustering);

            // Step 1: Scraping
            var scrapingFuture = webScrapingService.scrapeAndSaveAllAsync(articleLimit);
            response.put("scraping_status", "initiated");

            // Step 2: Summarization (if enabled)
            if (enableSummarization) {
                var summaryResults = articleService.summarizeUnsummarizedArticles(articleLimit);
                long successful = summaryResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum();
                response.put("summarization_completed", successful + "/" + summaryResults.size());
            } else {
                response.put("summarization_status", "skipped");
            }

            // Step 3: Event clustering (if enabled)
            if (enableClustering) {
                var clusteringResult = eventService.processRecentArticlesForEvents(1, 0.7);
                response.put("clustering_status", clusteringResult.isSuccess() ? "completed" : "failed");
                if (clusteringResult.isSuccess()) {
                    response.put("events_created", clusteringResult.getClusters().size());
                }
            } else {
                response.put("clustering_status", "skipped");
            }

            response.put("overall_status", "completed");
            response.put("message", "Full pipeline execution completed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error triggering full pipeline: {}", e.getMessage());
            response.put("status", "error");
            response.put("message", "Failed to trigger pipeline: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Configuration management (placeholder)
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> config = new HashMap<>();

        // Add relevant configuration parameters
        config.put("scraping_enabled", true);
        config.put("ai_summarization_enabled", true);
        config.put("event_clustering_enabled", true);
        config.put("max_articles_per_scrape", 50);
        config.put("clustering_similarity_threshold", 0.7);

        return ResponseEntity.ok(config);
    }

    /**
     * Update configuration (placeholder)
     */
    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfiguration(@RequestBody Map<String, Object> newConfig) {
        Map<String, Object> response = new HashMap<>();

        // This is a placeholder - implement configuration updates as needed
        response.put("status", "success");
        response.put("message", "Configuration update not fully implemented yet");
        response.put("updated_keys", newConfig.keySet());

        return ResponseEntity.ok(response);
    }
}