package com.factfeed.backend.config;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration class for news sources loaded from YAML.
 * <p>
 * This class defines the structure for YAML-based scraper configuration,
 * allowing dynamic selector definitions without code changes.
 * <p>
 * Design rationale:
 * - Eliminates hardcoded selectors in Java code
 * - Enables quick adaptation to website structure changes
 * - Supports priority-based selector fallbacks
 * - Allows JSON-LD structured data extraction
 */
@Data
@ConfigurationProperties(prefix = "sources")
@Component
public class NewsSourceConfig {
    private String name;
    private String baseUrl;

    // CSS selectors in priority order (high to low)
    private List<String> titleSelectors;
    private List<String> authorSelectors;
    private List<String> authorLocationSelectors;
    private List<String> publishedTimeSelectors;  // Fixed naming to match YAML
    private List<String> updatedTimeSelectors;    // Fixed naming to match YAML
    private List<String> imageSelectors;
    private List<String> imageCaptionSelectors;
    private List<String> contentSelectors;
    private List<String> contentIgnoreSelectors;  // Elements to remove from content
    private List<String> categorySelectors;
    private List<String> keywordSelectors;

    // Section configuration
    private List<String> categories;              // Sections to scrape
    private List<String> categoriesToIgnore;      // Sections to skip

    /**
     * Get the domain name from the base URL
     */
    public String getDomain() {
        if (baseUrl == null) return null;
        return baseUrl.replaceAll("https?://", "").replaceAll("/.*", "");
    }

    /**
     * Check if a URL belongs to this news source
     */
    public boolean matchesUrl(String url) {
        return url != null && url.toLowerCase().contains(getDomain().toLowerCase());
    }

    /**
     * Get section URLs for this news source
     */
    public List<String> getSectionUrls() {
        if (categories == null) return List.of();
        return categories.stream()
                .map(section -> baseUrl.endsWith("/") ? baseUrl + section.substring(1) : baseUrl + section)
                .toList();
    }
}