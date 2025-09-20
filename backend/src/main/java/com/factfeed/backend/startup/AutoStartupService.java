package com.factfeed.backend.startup;

import com.factfeed.backend.events.service.AggregationService;
import com.factfeed.backend.events.service.EventMappingService;
import com.factfeed.backend.scraper.service.ArticleScrapingService;
import com.factfeed.backend.ai.service.SummarizationService;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandlerFactory;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandler;
import com.factfeed.backend.scraper.model.NewsSource;
import org.openqa.selenium.WebDriver;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoStartupService {

    private final UrlDiscoveryHandlerFactory handlerFactory;
    private final WebDriver webDriver;
    private final ArticleScrapingService scrapingService;
    private final SummarizationService summarizationService;
    private final EventMappingService eventMappingService;
    private final AggregationService aggregationService;

    @Value("${app.startup.auto-scrape:true}")
    private boolean enableAutoScrape;

    @Value("${app.startup.scrape-delay:30}")
    private int startupDelaySeconds;

    @Value("${app.startup.max-urls-per-source:10}")
    private int maxUrlsPerSource;

    private volatile boolean isStartupComplete = false;

    /**
     * Automatically start scraping and processing when application is ready
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        if (!enableAutoScrape) {
            log.info("🔕 Auto-scraping disabled via configuration");
            return;
        }

        log.info("🚀 APPLICATION READY - Starting automated FactFeed pipeline in {} seconds...", startupDelaySeconds);
        
        try {
            // Give application time to fully initialize
            Thread.sleep(startupDelaySeconds * 1000);
            
            executeStartupPipeline();
            
        } catch (InterruptedException e) {
            log.error("❌ Startup pipeline interrupted: {}", e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Execute the complete startup pipeline
     */
    private void executeStartupPipeline() {
        log.info("🌟 ===== FACTFEED AUTO-STARTUP PIPELINE STARTED =====");
        log.info("📅 Start Time: {}", LocalDateTime.now());
        
        try {
            // Phase 1: Scraping
            log.info("🔍 PHASE 1: Article Scraping");
            CompletableFuture<Void> scrapingFuture = performAutomaticScraping();
            
            // Phase 2: Summarization (after scraping completes)
            log.info("📄 PHASE 2: Article Summarization");
            scrapingFuture.thenRun(this::performAutomaticSummarization)
                         .thenRun(this::performEventProcessing)
                         .thenRun(this::onStartupComplete)
                         .exceptionally(this::handleStartupError);
                         
        } catch (Exception e) {
            log.error("❌ Startup pipeline failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Phase 1: Automatic scraping from all sources
     */
    @Async
    public CompletableFuture<Void> performAutomaticScraping() {
        log.info("🕷️ Starting automatic scraping from all news sources...");
        
        try {
            int totalArticles = 0;
            int successfulSources = 0;
            
            for (NewsSource source : NewsSource.values()) {
                try {
                    log.info("📰 Scraping from: {} (max {} URLs)", source.getDisplayName(), maxUrlsPerSource);
                    
                    // Create URL discovery handler for this source
                    UrlDiscoveryHandler handler = handlerFactory.createHandler(webDriver, source);
                    List<String> urls = handler.discoveredUrls(maxUrlsPerSource);
                    log.info("🔗 Found {} URLs for {}", urls.size(), source.getDisplayName());
                    
                    // Scrape articles
                    if (!urls.isEmpty()) {
                        var result = scrapingService.scrapeArticlesFromUrls(urls, source);
                        totalArticles += result.size();
                        successfulSources++;
                        log.info("✅ Scraped {} articles from {}", result.size(), source.getDisplayName());
                        
                        // Small delay between sources
                        Thread.sleep(3000);
                    }
                    
                } catch (Exception e) {
                    log.error("❌ Failed to scrape from {}: {}", source.getDisplayName(), e.getMessage());
                }
            }
            
            log.info("🎯 SCRAPING COMPLETE: {} articles from {}/{} sources", 
                    totalArticles, successfulSources, NewsSource.values().length);
                    
        } catch (Exception e) {
            log.error("❌ Automatic scraping failed: {}", e.getMessage());
            throw new RuntimeException("Scraping phase failed", e);
        }
        
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Phase 2: Automatic summarization
     */
    private void performAutomaticSummarization() {
        log.info("📝 Starting automatic summarization...");
        
        try {
            String result = summarizationService.summarizeAllUnsummarized();
            log.info("✅ Summarization completed: {}", result);
            
        } catch (Exception e) {
            log.error("❌ Automatic summarization failed: {}", e.getMessage());
            throw new RuntimeException("Summarization phase failed", e);
        }
    }

    /**
     * Phase 3: Event processing (single-batch mapping + aggregation)
     */
    private void performEventProcessing() {
        log.info("🎪 Starting automatic event processing...");
        
        try {
            // Step 1: Single-Batch Event Mapping
            log.info("📍 Step 1: Single-Batch Event Mapping");
            String mappingResult = eventMappingService.mapAllUnmappedArticles();
            log.info("✅ Single-batch mapping completed: {}", mappingResult);
            
            // Step 2: Event Aggregation (now with source-specific discrepancy detection)
            log.info("📍 Step 2: Event Aggregation with Source Analysis");
            String aggregationResult = aggregationService.processAllUnprocessedEvents();
            log.info("✅ Aggregation completed: {}", aggregationResult);
            
        } catch (Exception e) {
            log.error("❌ Automatic event processing failed: {}", e.getMessage());
            throw new RuntimeException("Event processing phase failed", e);
        }
    }

    /**
     * Pipeline completion handler
     */
    private void onStartupComplete() {
        isStartupComplete = true;
        log.info("🎉 ===== FACTFEED AUTO-STARTUP PIPELINE COMPLETED =====");
        log.info("📅 Completion Time: {}", LocalDateTime.now());
        log.info("🌐 FactFeed is now ready with fresh content!");
    }

    /**
     * Pipeline error handler
     */
    private Void handleStartupError(Throwable throwable) {
        log.error("💥 ===== FACTFEED AUTO-STARTUP PIPELINE FAILED =====");
        log.error("❌ Error: {}", throwable.getMessage(), throwable);
        log.info("🔧 Manual intervention may be required");
        return null;
    }

    /**
     * Scheduled task to run pipeline every 4 hours
     */
    @Scheduled(fixedRate = 4 * 60 * 60 * 1000) // 4 hours
    public void scheduledPipelineExecution() {
        if (!isStartupComplete) {
            log.info("⏳ Skipping scheduled execution - startup still in progress");
            return;
        }
        
        log.info("⏰ SCHEDULED PIPELINE EXECUTION STARTED");
        executeStartupPipeline();
    }

    /**
     * Scheduled task to run light refresh every hour
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // 1 hour  
    public void hourlyRefresh() {
        if (!isStartupComplete) {
            return;
        }
        
        log.info("🔄 HOURLY REFRESH: Light scraping + processing");
        
        try {
            // Light scraping (fewer URLs)
            for (NewsSource source : NewsSource.values()) {
                try {
                    UrlDiscoveryHandler handler = handlerFactory.createHandler(webDriver, source);
                    List<String> urls = handler.discoveredUrls(3); // Only 3 URLs
                    if (!urls.isEmpty()) {
                        scrapingService.scrapeArticlesFromUrls(urls, source);
                    }
                    Thread.sleep(2000);
                } catch (Exception e) {
                    log.debug("Light scraping error for {}: {}", source.getDisplayName(), e.getMessage());
                }
            }
            
            // Quick processing
            summarizationService.summarizeAllUnsummarized();
            eventMappingService.mapAllUnmappedArticles();
            
            log.info("✅ Hourly refresh completed");
            
        } catch (Exception e) {
            log.error("❌ Hourly refresh failed: {}", e.getMessage());
        }
    }

    /**
     * Check if startup pipeline is complete
     */
    public boolean isStartupComplete() {
        return isStartupComplete;
    }
}