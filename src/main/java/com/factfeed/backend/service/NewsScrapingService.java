package com.factfeed.backend.service;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.NewsSource;
import com.factfeed.backend.repository.ArticleRepository;
import com.factfeed.backend.scraper.Scraper;
import com.factfeed.backend.scraper.ScraperFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service that orchestrates news scraping from all configured sources.
 * Implements the first step of the 4-step pipeline: Scrape.
 */
@Service
public class NewsScrapingService {
    
    private static final Logger log = LoggerFactory.getLogger(NewsScrapingService.class);
    
    @Autowired
    private NewsSourceConfig newsSourceConfig;
    
    @Autowired
    private ScraperFactory scraperFactory;
    
    @Autowired
    private ArticleRepository articleRepository;
    
    @Value("${factfeed.scraping.enabled:true}")
    private boolean scrapingEnabled;
    
    /**
     * Scrape articles from all configured sources.
     * This is the main entry point for the scraping pipeline.
     */
    public List<Article> scrapeAllSources() {
        if (!scrapingEnabled) {
            log.info("Scraping is disabled");
            return new ArrayList<>();
        }
        
        if (!newsSourceConfig.hasSourcesLoaded()) {
            log.error("No news sources loaded - cannot proceed with scraping");
            return new ArrayList<>();
        }
        
        log.info("Starting scraping from all sources");
        List<Article> allArticles = new ArrayList<>();
        List<NewsSource> sources = newsSourceConfig.getSources();
        
        for (NewsSource source : sources) {
            try {
                List<Article> articles = scrapeFromSource(source);
                allArticles.addAll(articles);
                log.info("Scraped {} articles from {}", articles.size(), source.getName());
            } catch (Exception e) {
                log.error("Failed to scrape from source {}: {}", source.getName(), e.getMessage(), e);
            }
        }
        
        // Save all articles to database
        if (!allArticles.isEmpty()) {
            List<Article> savedArticles = articleRepository.saveAll(allArticles);
            log.info("Successfully saved {} articles to database", savedArticles.size());
            return savedArticles;
        }
        
        log.info("No new articles found during scraping");
        return allArticles;
    }
    
    /**
     * Scrape articles from a specific source.
     */
    public List<Article> scrapeFromSource(NewsSource source) {
        if (source == null) {
            log.warn("Cannot scrape from null source");
            return new ArrayList<>();
        }
        
        log.debug("Starting scraping from source: {}", source.getName());
        
        Scraper scraper = scraperFactory.getScraper(source.getType());
        if (scraper == null) {
            log.error("No scraper available for type: {} (source: {})", source.getType(), source.getName());
            return new ArrayList<>();
        }
        
        try {
            List<Article> articles = scraper.scrape(source);
            log.debug("Scraped {} articles from {}", articles.size(), source.getName());
            return articles;
        } catch (Exception e) {
            log.error("Error scraping from source {}: {}", source.getName(), e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Scrape from a specific source by name.
     */
    public List<Article> scrapeFromSourceByName(String sourceName) {
        NewsSource source = newsSourceConfig.getSourceByName(sourceName);
        if (source == null) {
            log.warn("Source not found: {}", sourceName);
            return new ArrayList<>();
        }
        
        List<Article> articles = scrapeFromSource(source);
        
        // Save articles to database
        if (!articles.isEmpty()) {
            articles = articleRepository.saveAll(articles);
        }
        
        return articles;
    }
    
    /**
     * Get statistics about scraped articles.
     */
    public ScrapingStats getScrapingStats() {
        ScrapingStats stats = new ScrapingStats();
        
        List<NewsSource> sources = newsSourceConfig.getSources();
        for (NewsSource source : sources) {
            long count = articleRepository.countBySourceName(source.getName());
            stats.addSourceCount(source.getName(), count);
        }
        
        stats.setTotalArticles(articleRepository.count());
        stats.setAvailableScrapers(scraperFactory.getAvailableTypes());
        
        return stats;
    }
    
    /**
     * Statistics class for scraping information.
     */
    public static class ScrapingStats {
        private long totalArticles;
        private java.util.Map<String, Long> articlesBySource = new java.util.HashMap<>();
        private java.util.Set<String> availableScrapers;
        
        public long getTotalArticles() {
            return totalArticles;
        }
        
        public void setTotalArticles(long totalArticles) {
            this.totalArticles = totalArticles;
        }
        
        public java.util.Map<String, Long> getArticlesBySource() {
            return articlesBySource;
        }
        
        public void addSourceCount(String sourceName, long count) {
            articlesBySource.put(sourceName, count);
        }
        
        public java.util.Set<String> getAvailableScrapers() {
            return availableScrapers;
        }
        
        public void setAvailableScrapers(java.util.Set<String> availableScrapers) {
            this.availableScrapers = availableScrapers;
        }
    }
}