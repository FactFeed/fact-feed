package com.factfeed.backend.scraper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Factory for creating appropriate scrapers based on source type.
 * Provides extensible architecture for adding new scraper implementations.
 */
@Component
public class ScraperFactory {
    
    private final Map<String, Scraper> scrapers = new HashMap<>();
    
    @Autowired
    public ScraperFactory(List<Scraper> scraperBeans) {
        // Auto-register all scraper beans by their type
        for (Scraper scraper : scraperBeans) {
            scrapers.put(scraper.getType(), scraper);
        }
    }
    
    /**
     * Get a scraper instance for the given type.
     * 
     * @param type The scraper type (e.g., "web_scraping", "rss")
     * @return Scraper instance, or null if not found
     */
    public Scraper getScraper(String type) {
        return scrapers.get(type);
    }
    
    /**
     * Check if a scraper is available for the given type.
     */
    public boolean hasScraperForType(String type) {
        return scrapers.containsKey(type);
    }
    
    /**
     * Get all available scraper types.
     */
    public java.util.Set<String> getAvailableTypes() {
        return scrapers.keySet();
    }
}