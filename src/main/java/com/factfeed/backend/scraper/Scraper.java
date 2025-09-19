package com.factfeed.backend.scraper;

import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.NewsSource;
import java.util.List;

/**
 * Base interface for all news scrapers.
 * Provides extensible architecture for adding new scraper types.
 */
public interface Scraper {
    
    /**
     * Scrapes articles from the given news source.
     * 
     * @param source The news source configuration containing URLs and selectors
     * @return List of scraped articles, empty list if none found or error occurred
     */
    List<Article> scrape(NewsSource source);
    
    /**
     * Gets the type of scraper (e.g., "web_scraping", "rss", etc.)
     * 
     * @return The scraper type identifier
     */
    String getType();
}