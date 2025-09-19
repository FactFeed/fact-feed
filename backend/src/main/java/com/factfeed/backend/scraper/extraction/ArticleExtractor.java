package com.factfeed.backend.scraper.extraction;

import com.factfeed.backend.scraper.model.ArticleData;
import com.factfeed.backend.scraper.model.ValidationResult;

/**
 * Interface for extracting article data from different news sources
 */
public interface ArticleExtractor {

    /**
     * Extract article data from a given URL
     *
     * @param url      The URL to extract from
     * @param siteName The name of the site being extracted from
     * @return ArticleData containing extracted information
     */
    ArticleData extractArticle(String url, String siteName);

    /**
     * Validate if the extractor can handle the given URL
     *
     * @param url The URL to validate
     * @return ValidationResult indicating if URL is supported
     */
    ValidationResult validateUrl(String url);

    /**
     * Get the site name this extractor handles
     *
     * @return Site name
     */
    String getSiteName();

    /**
     * Get extraction method used (e.g., "JSON-LD", "HTML", "META")
     *
     * @return Extraction method
     */
    String getExtractionMethod();
}