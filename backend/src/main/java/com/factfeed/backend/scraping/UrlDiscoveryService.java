package com.factfeed.backend.scraping;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.config.ScrapingConfig;
import com.factfeed.backend.model.enums.NewsSource;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Service to discover article URLs from news source category pages
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UrlDiscoveryService {

    private final ScrapingConfig scrapingConfig;
    private final NewsSourceConfigService configService;

    /**
     * Discover article URLs from a news source's category pages
     */
    public List<String> discoverUrls(NewsSource source, int limit) {
        log.info("Discovering URLs for {} with limit {}", source, limit);

        NewsSourceConfig config = configService.getConfig(source);
        if (config == null) {
            log.error("No configuration found for news source: {}", source);
            return new ArrayList<>();
        }

        Set<String> discoveredUrls = new HashSet<>();
        List<String> categoryUrls = getCategoryUrls(config);

        for (String categoryUrl : categoryUrls) {
            if (discoveredUrls.size() >= limit) {
                break;
            }

            try {
                List<String> urlsFromCategory = discoverUrlsFromPage(categoryUrl, config, source);
                discoveredUrls.addAll(urlsFromCategory);

                // Add delay between requests
                Thread.sleep((long) (scrapingConfig.getDefaultDelay() * 1000));

            } catch (Exception e) {
                log.error("Error discovering URLs from category {}: {}", categoryUrl, e.getMessage());
            }
        }

        List<String> result = new ArrayList<>(discoveredUrls);
        log.info("Discovered {} URLs for {}", result.size(), source);
        return result.subList(0, Math.min(result.size(), limit));
    }

    /**
     * Discover article URLs from a specific page
     */
    public List<String> discoverUrlsFromPage(String pageUrl, NewsSourceConfig config, NewsSource source) {
        log.debug("Discovering URLs from page: {}", pageUrl);

        Set<String> urls = new HashSet<>();

        try {
            Document doc = Jsoup.connect(pageUrl)
                    .userAgent(scrapingConfig.getDefaultHeaders().get("User-Agent"))
                    .timeout(scrapingConfig.getDefaultTimeout() * 1000)
                    .headers(scrapingConfig.getDefaultHeaders())
                    .get();

            // Find all links on the page
            Elements links = doc.select("a[href]");

            for (Element link : links) {
                String href = link.attr("href");
                String fullUrl = resolveUrl(pageUrl, href);

                if (fullUrl != null && isValidArticleUrl(fullUrl, config, source)) {
                    urls.add(fullUrl);
                }
            }

        } catch (IOException e) {
            log.error("Error fetching page {}: {}", pageUrl, e.getMessage());
        } catch (Exception e) {
            log.error("Error processing page {}: {}", pageUrl, e.getMessage());
        }

        return new ArrayList<>(urls);
    }

    /**
     * Get category URLs for a news source
     */
    private List<String> getCategoryUrls(NewsSourceConfig config) {
        List<String> categoryUrls = new ArrayList<>();

        // Add base URL
        categoryUrls.add(config.getBaseUrl());

        // Add category URLs
        if (config.getCategories() != null) {
            for (String category : config.getCategories()) {
                String categoryUrl = buildCategoryUrl(config.getBaseUrl(), category);
                categoryUrls.add(categoryUrl);
            }
        }

        return categoryUrls;
    }

    /**
     * Build category URL from base URL and category path
     */
    private String buildCategoryUrl(String baseUrl, String category) {
        if (baseUrl.endsWith("/") && category.startsWith("/")) {
            return baseUrl + category.substring(1);
        } else if (!baseUrl.endsWith("/") && !category.startsWith("/")) {
            return baseUrl + "/" + category;
        } else {
            return baseUrl + category;
        }
    }

    /**
     * Resolve relative URL to absolute URL
     */
    private String resolveUrl(String baseUrl, String href) {
        try {
            // Skip JavaScript and other invalid schemes
            if (href == null || href.trim().isEmpty() ||
                    href.startsWith("javascript:") || href.startsWith("mailto:") ||
                    href.startsWith("#") || href.startsWith("tel:")) {
                return null;
            }

            // If already absolute URL, return as is
            if (href.startsWith("http://") || href.startsWith("https://")) {
                return href;
            }

            URI baseUri = new URI(baseUrl);
            URI resolvedUri = baseUri.resolve(href);
            return resolvedUri.toString();
        } catch (URISyntaxException e) {
            log.debug("Error resolving URL {} with base {}: {}", href, baseUrl, e.getMessage());
            return null;
        }
    }

    /**
     * Check if URL is a valid article URL for the given source
     */
    private boolean isValidArticleUrl(String url, NewsSourceConfig config, NewsSource source) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }

        // Must belong to the correct source
        if (!source.matchesUrl(url)) {
            return false;
        }

        // Must be HTTP/HTTPS
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // Check against excluded patterns
        String lowerUrl = url.toLowerCase();
        boolean excluded = scrapingConfig.getExcludedUrlPatterns().stream()
                .anyMatch(pattern -> lowerUrl.contains(pattern.toLowerCase()));
        if (excluded) {
            return false;
        }

        // Check against ignored categories
        if (config.getCategoriesToIgnore() != null) {
            boolean inIgnoredCategory = config.getCategoriesToIgnore().stream()
                    .anyMatch(ignoredCategory -> lowerUrl.contains(ignoredCategory.toLowerCase()));
            if (inIgnoredCategory) {
                return false;
            }
        }

        // Additional heuristics for article URLs
        return isLikelyArticleUrl(url);
    }

    /**
     * Use heuristics to determine if URL is likely an article
     */
    private boolean isLikelyArticleUrl(String url) {
        String lowerUrl = url.toLowerCase();

        // Exclude common non-article patterns from config
        for (String pattern : scrapingConfig.getExcludedUrlPatterns()) {
            if (lowerUrl.contains(pattern.toLowerCase())) {
                return false;
            }
        }

        // Common article URL patterns
        boolean hasDatePattern = url.matches(".*/(\\d{4})/(\\d{1,2})/(\\d{1,2})/.*");
        boolean hasArticleId = url.matches(".*/\\d{6,}/.*"); // 6+ digit article IDs
        boolean hasArticleKeyword = lowerUrl.contains("/news/") ||
                lowerUrl.contains("/article/") ||
                lowerUrl.contains("/story/") ||
                lowerUrl.contains("/post/");

        // Must have reasonable length (not just domain or category)
        String path = url.replaceFirst("https?://[^/]+", "");
        boolean hasSubstantialPath = path.length() > 15;

        // Should not end with common non-article patterns
        boolean endsWithBadPattern = lowerUrl.endsWith("/") ||
                lowerUrl.endsWith(".html#") ||
                lowerUrl.endsWith(".php") ||
                lowerUrl.contains("#");

        return (hasDatePattern || hasArticleId || hasArticleKeyword || hasSubstantialPath) && !endsWithBadPattern;
    }
}