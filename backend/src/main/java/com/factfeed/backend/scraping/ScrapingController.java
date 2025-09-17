package com.factfeed.backend.scraping;

import com.factfeed.backend.model.dto.ScrapingRequestDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.dto.ScrapingStatusDTO;
import com.factfeed.backend.model.enums.NewsSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for web scraping operations
 */
@RestController
@RequestMapping("/api/scraping")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ScrapingController {

    private final WebScrapingService webScrapingService;
    private final UrlDiscoveryService urlDiscoveryService;

    /**
     * Scrape articles from all news sources synchronously
     */
    @PostMapping("/scrape-all")
    public ResponseEntity<?> scrapeAllSources(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limitPerSource) {

        try {
            log.info("Starting synchronous scraping for all sources with limit {}", limitPerSource);

            ScrapingResultDTO result = webScrapingService.scrapeAllSources(limitPerSource);

            return ResponseEntity.ok(Map.of(
                    "message", "Scraping completed successfully",
                    "result", result,
                    "summary", Map.of(
                            "totalArticles", result.getArticles().size(),
                            "totalRequested", result.getTotalRequested(),
                            "successRate", result.getSuccessRate(),
                            "durationSeconds", result.getDurationSeconds()
                    )
            ));

        } catch (Exception e) {
            log.error("Error during synchronous scraping for all sources", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Scraping failed: " + e.getMessage()));
        }
    }

    /**
     * Scrape articles from all news sources asynchronously
     */
    @PostMapping("/scrape-all-async")
    public ResponseEntity<?> scrapeAllSourcesAsync(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limitPerSource) {

        try {
            log.info("Starting asynchronous scraping for all sources with limit {}", limitPerSource);

            CompletableFuture<ScrapingResultDTO> future = webScrapingService.scrapeAndSaveAllAsync(limitPerSource);

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Async scraping started for all sources",
                    "limitPerSource", limitPerSource,
                    "status", "RUNNING",
                    "checkStatusAt", "/api/scraping/status"
            ));

        } catch (Exception e) {
            log.error("Error starting async scraping for all sources", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start scraping: " + e.getMessage()));
        }
    }

    /**
     * Scrape articles from a specific news source synchronously
     */
    @PostMapping("/scrape/{source}")
    public ResponseEntity<?> scrapeSource(
            @PathVariable NewsSource source,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        try {
            log.info("Starting synchronous scraping for {} with limit {}", source, limit);

            ScrapingResultDTO result = webScrapingService.scrapeSource(source, limit);

            return ResponseEntity.ok(Map.of(
                    "message", "Scraping completed successfully",
                    "source", source.name(),
                    "result", result,
                    "summary", Map.of(
                            "articlesFound", result.getArticles().size(),
                            "urlsProcessed", result.getTotalRequested(),
                            "successRate", result.getSuccessRate(),
                            "durationSeconds", result.getDurationSeconds()
                    )
            ));

        } catch (Exception e) {
            log.error("Error during synchronous scraping for {}", source, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Scraping failed for " + source + ": " + e.getMessage()));
        }
    }

    /**
     * Scrape articles from a specific news source asynchronously
     */
    @PostMapping("/scrape/{source}/async")
    public ResponseEntity<?> scrapeSourceAsync(
            @PathVariable NewsSource source,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int limit) {

        try {
            log.info("Starting asynchronous scraping for {} with limit {}", source, limit);

            CompletableFuture<ScrapingResultDTO> future = webScrapingService.scrapeAndSaveAsync(source, limit);

            return ResponseEntity.accepted().body(Map.of(
                    "message", "Async scraping started",
                    "source", source.name(),
                    "limit", limit,
                    "status", "RUNNING",
                    "checkStatusAt", "/api/scraping/status"
            ));

        } catch (Exception e) {
            log.error("Error starting async scraping for {}", source, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start scraping for " + source + ": " + e.getMessage()));
        }
    }

    /**
     * Scrape specific URLs
     */
    @PostMapping("/scrape-urls")
    public ResponseEntity<?> scrapeUrls(@Valid @RequestBody ScrapingRequestDTO request) {
        try {
            if (request.getUrls() == null || request.getUrls().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "URLs list cannot be empty"));
            }

            if (request.getUrls().size() > 50) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Maximum 50 URLs allowed per request"));
            }

            // Determine source from URLs if not provided
            NewsSource source = null;
            if (request.getSourceName() != null) {
                source = NewsSource.fromName(request.getSourceName());
            }

            if (source == null) {
                // Try to infer from first URL
                source = NewsSource.fromUrl(request.getUrls().get(0));
            }

            if (source == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not determine news source"));
            }

            log.info("Scraping {} specific URLs for {}", request.getUrls().size(), source);

            ScrapingResultDTO result = webScrapingService.scrapeUrls(request.getUrls(), source);

            return ResponseEntity.ok(Map.of(
                    "message", "URL scraping completed",
                    "source", source.name(),
                    "result", result
            ));

        } catch (Exception e) {
            log.error("Error scraping specific URLs", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "URL scraping failed: " + e.getMessage()));
        }
    }

    /**
     * Get scraping task status
     */
    @GetMapping("/status/{taskId}")
    public ResponseEntity<?> getTaskStatus(@PathVariable String taskId) {
        ScrapingStatusDTO status = webScrapingService.getTaskStatus(taskId);

        if (status == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Get all scraping task statuses
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, ScrapingStatusDTO>> getAllTaskStatuses() {
        return ResponseEntity.ok(webScrapingService.getAllTaskStatuses());
    }

    /**
     * Discover URLs from a news source without scraping
     */
    @GetMapping("/discover-urls/{source}")
    public ResponseEntity<?> discoverUrls(
            @PathVariable NewsSource source,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {

        try {
            log.info("Discovering URLs for {} with limit {}", source, limit);

            List<String> urls = urlDiscoveryService.discoverUrls(source, limit);

            return ResponseEntity.ok(Map.of(
                    "message", "URL discovery completed",
                    "source", source.name(),
                    "limit", limit,
                    "urls", urls,
                    "count", urls.size()
            ));

        } catch (Exception e) {
            log.error("Error discovering URLs for {}", source, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "URL discovery failed: " + e.getMessage()));
        }
    }

    /**
     * Health check for scraping service
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "FactFeed Scraping Service",
                "timestamp", java.time.LocalDateTime.now().toString(),
                "supportedSources", List.of(NewsSource.values())
        ));
    }

    /**
     * Save scraped articles to database (for external scrapers)
     */
    @PostMapping("/save-results")
    public ResponseEntity<?> saveScrapingResults(@Valid @RequestBody ScrapingResultDTO scrapingResult) {
        try {
            log.info("Saving scraping results from external source: {}", scrapingResult.getSiteName());

            // This endpoint allows external scrapers to save their results
            // The ArticleService already handles this
            return ResponseEntity.ok(Map.of(
                    "message", "Use /api/articles/submit endpoint for saving scraped articles"
            ));

        } catch (Exception e) {
            log.error("Error processing external scraping results", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save results: " + e.getMessage()));
        }
    }
}