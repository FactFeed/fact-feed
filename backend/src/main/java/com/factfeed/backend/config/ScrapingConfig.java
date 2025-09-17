package com.factfeed.backend.config;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "scraping")
@Data
public class ScrapingConfig {

    // Default configurations
    private double defaultDelay = 1.0;
    private int defaultMaxRetries = 3;
    private int defaultTimeout = 30;
        // Enable limited concurrency per source when extracting articles
        private boolean parallelPerSourceEnabled = true;
        private int maxConcurrentPerSource = 3;

    // Default headers for HTTP requests
    private Map<String, String> defaultHeaders = Map.of(
            "User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8",
            "Accept-Language", "en-US,en;q=0.5",
            "Accept-Encoding", "gzip, deflate"
    );

    // URL patterns to exclude
    private List<String> excludedUrlPatterns = Arrays.asList(
            "javascript:", "mailto:", "#",
            "facebook.com", "twitter.com", "youtube.com", "instagram.com",
            "whatsapp.com", "telegram.org", "linkedin.com",
            "/search", "/category", "/tag", "/author",
            "share", "print", "pdf", "email"
    );

    // Image URL patterns to exclude
    private List<String> excludedImagePatterns = Arrays.asList(
            "logo", "icon", "avatar", "profile", "banner", "advertisement",
            "ads", "social", "share", "button", "thumbnail_small"
    );
}