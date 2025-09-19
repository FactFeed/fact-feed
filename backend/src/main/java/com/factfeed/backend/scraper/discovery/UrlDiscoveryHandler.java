package com.factfeed.backend.scraper.discovery;

import java.util.List;
import java.util.Set;
import org.openqa.selenium.WebDriver;

/**
 * Interface for site-specific URL discovery handlers
 */
public interface UrlDiscoveryHandler {

    /**
     * Discover article URLs from a news site's latest page
     *
     * @param driver   WebDriver instance for handling dynamic content
     * @param baseUrl  Base URL of the news site
     * @param maxCount Maximum number of URLs to discover
     * @param stopUrls Set of existing URLs to stop discovery when encountered
     * @return List of discovered article URLs
     */
    List<String> discoverUrls(WebDriver driver, String baseUrl, int maxCount, Set<String> stopUrls);

    /**
     * Get the site name this handler is for
     */
    String getSiteName();
}