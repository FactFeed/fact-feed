package com.factfeed.backend.controller;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.NewsSource;
import com.factfeed.backend.service.NewsScrapingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Test controller for manual verification of scraper functionality.
 * Should be used during development and testing phases only.
 */
@RestController
@RequestMapping("/api/test")
public class ScrapingTestController {
    
    private static final Logger log = LoggerFactory.getLogger(ScrapingTestController.class);
    
    @Autowired
    private NewsScrapingService scrapingService;
    
    @Autowired
    private NewsSourceConfig newsSourceConfig;
    
    /**
     * Test scraping from a specific source by name.
     */
    @GetMapping("/scrape/{sourceName}")
    public ResponseEntity<Map<String, Object>> testScraping(@PathVariable String sourceName) {
        log.info("Testing scraping for source: {}", sourceName);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Article> articles = scrapingService.scrapeFromSourceByName(sourceName);
            
            response.put("success", true);
            response.put("sourceName", sourceName);
            response.put("articlesFound", articles.size());
            response.put("articles", articles);
            response.put("message", "Successfully scraped " + articles.size() + " articles from " + sourceName);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to test scraping for source {}: {}", sourceName, e.getMessage(), e);
            
            response.put("success", false);
            response.put("sourceName", sourceName);
            response.put("error", e.getMessage());
            response.put("message", "Failed to scrape from " + sourceName);
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Test scraping from all sources.
     */
    @GetMapping("/scrape-all")
    public ResponseEntity<Map<String, Object>> testScrapeAll() {
        log.info("Testing scraping from all sources");
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<Article> articles = scrapingService.scrapeAllSources();
            
            response.put("success", true);
            response.put("totalArticles", articles.size());
            response.put("articles", articles);
            response.put("message", "Successfully scraped " + articles.size() + " articles from all sources");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to test scraping from all sources: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("message", "Failed to scrape from all sources");
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Validate CSS selectors for a specific source.
     */
    @GetMapping("/validate-selectors/{sourceName}")
    public ResponseEntity<Map<String, Object>> validateSelectors(@PathVariable String sourceName) {
        log.info("Validating selectors for source: {}", sourceName);
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            NewsSource source = newsSourceConfig.getSourceByName(sourceName);
            
            if (source == null) {
                response.put("success", false);
                response.put("error", "Source not found: " + sourceName);
                return ResponseEntity.badRequest().body(response);
            }
            
            // TODO: Implement actual selector validation
            // This would involve connecting to the source and testing selectors
            
            response.put("success", true);
            response.put("sourceName", sourceName);
            response.put("source", source);
            response.put("message", "Source configuration loaded successfully");
            response.put("note", "Detailed selector validation not yet implemented");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to validate selectors for source {}: {}", sourceName, e.getMessage(), e);
            
            response.put("success", false);
            response.put("sourceName", sourceName);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Get scraping statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<NewsScrapingService.ScrapingStats> getScrapingStats() {
        try {
            NewsScrapingService.ScrapingStats stats = scrapingService.getScrapingStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Failed to get scraping stats: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * List all available sources.
     */
    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            List<NewsSource> sources = newsSourceConfig.getSources();
            
            response.put("success", true);
            response.put("totalSources", sources.size());
            response.put("sources", sources);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Failed to list sources: {}", e.getMessage(), e);
            
            response.put("success", false);
            response.put("error", e.getMessage());
            
            return ResponseEntity.badRequest().body(response);
        }
    }
}