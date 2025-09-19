package com.factfeed.backend.scraper.controller;

import com.factfeed.backend.database.service.ArticleService;
import com.factfeed.backend.scraper.model.ArticleData;
import com.factfeed.backend.scraper.model.ScrapingResult;
import com.factfeed.backend.scraper.service.ScrapingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scraper")
@Slf4j
public class ScrapingController {

    private final ScrapingService scrapingService;
    private final ArticleService articleService;

    @Autowired
    public ScrapingController(ScrapingService scrapingService, ArticleService articleService) {
        this.scrapingService = scrapingService;
        this.articleService = articleService;
    }

    @PostMapping("/scrape/all")
    public ResponseEntity<ScrapingResult> scrapeAllSites() {
        log.info("API request: Scrape all sites");

        try {
            ScrapingResult result = scrapingService.scrapeAllSites();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error scraping all sites: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/scrape/{siteName}")
    public ResponseEntity<ScrapingResult> scrapeSite(@PathVariable String siteName) {
        log.info("API request: Scrape site {}", siteName);

        try {
            ScrapingResult result = scrapingService.scrapeSite(siteName);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid site name: {}", siteName);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error scraping site {}: {}", siteName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        log.info("API request: Get scraping stats");

        try {
            Map<String, Object> stats = new HashMap<>();
            stats.put("scraping", scrapingService.getScrapingStats());
            stats.put("storage", articleService.getStorageStats());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting stats: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/articles/{siteName}")
    public ResponseEntity<List<ArticleData>> getArticles(
            @PathVariable String siteName,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("API request: Get articles for {} (offset={}, limit={})", siteName, offset, limit);

        try {
            List<ArticleData> articles = articleService.getArticles(siteName, offset, limit);
            return ResponseEntity.ok(articles);
        } catch (Exception e) {
            log.error("Error getting articles for {}: {}", siteName, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/articles/recent")
    public ResponseEntity<List<ArticleData>> getRecentArticles(
            @RequestParam(defaultValue = "50") int limit) {

        log.info("API request: Get recent articles (limit={})", limit);

        try {
            List<ArticleData> articles = articleService.getRecentArticles(limit);
            return ResponseEntity.ok(articles);
        } catch (Exception e) {
            log.error("Error getting recent articles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/articles/search")
    public ResponseEntity<List<ArticleData>> searchArticles(
            @RequestParam String query,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("API request: Search articles with query '{}' (limit={})", query, limit);

        try {
            List<ArticleData> articles = articleService.searchArticles(query, limit);
            return ResponseEntity.ok(articles);
        } catch (Exception e) {
            log.error("Error searching articles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/scrape/advanced")
    public ResponseEntity<?> scrapeAdvanced(@RequestBody ScrapeRequest request) {
        log.info("API request: Advanced scrape with parameters: {}", request);

        try {
            ScrapingResult result;
            if (request.getSiteName() != null) {
                result = scrapingService.scrapeSite(request.getSiteName());
            } else {
                result = scrapingService.scrapeAllSites();
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error in advanced scraping: {}", e.getMessage(), e);

            Map<String, String> error = new HashMap<>();
            error.put("error", "Scraping failed");
            error.put("message", e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }

    @Data
    public static class ScrapeRequest {
        private String siteName;
        private Integer maxUrls;
        private Boolean forceRefresh;
    }
}