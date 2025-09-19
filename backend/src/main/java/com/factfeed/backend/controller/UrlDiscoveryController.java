package com.factfeed.backend.controller;

import com.factfeed.backend.scraper.model.NewsSource;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandler;
import com.factfeed.backend.scraper.urldiscovery.UrlDiscoveryHandlerFactory;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Slf4j
public class UrlDiscoveryController {

    private final UrlDiscoveryHandlerFactory handlerFactory;
    private final WebDriver webDriver;

    @GetMapping("/urls/all")
    public ResponseEntity<UrlDiscoveryResponse> discoverAllSources(
            @RequestParam(defaultValue = "100") int maxUrlsPerSource) {

        log.info("Starting URL discovery for all sources, maxUrlsPerSource: {}", maxUrlsPerSource);

        List<SourceDiscoveryResult> results = new ArrayList<>();
        LocalDateTime startTime = LocalDateTime.now();
        int totalUrls = 0;
        int successfulSources = 0;

        for (NewsSource source : NewsSource.values()) {
            log.info("Discovering URLs for source: {}", source.getDisplayName());

            try {
                UrlDiscoveryHandler handler = handlerFactory.createHandler(webDriver, source);
                List<String> urls = handler.discoveredUrls(maxUrlsPerSource);

                SourceDiscoveryResult result = new SourceDiscoveryResult(
                        source.name(),
                        source.getDisplayName(),
                        source.getBaseUrl(),
                        urls.size(),
                        urls,
                        true,
                        null
                );

                results.add(result);
                totalUrls += urls.size();
                successfulSources++;

                log.info("✅ {} - Found {} URLs", source.getDisplayName(), urls.size());
                urls.forEach(url -> log.debug("   - {}", url));

            } catch (Exception e) {
                log.error("❌ {} - Error: {}", source.getDisplayName(), e.getMessage(), e);

                SourceDiscoveryResult result = new SourceDiscoveryResult(
                        source.name(),
                        source.getDisplayName(),
                        source.getBaseUrl(),
                        0,
                        List.of(),
                        false,
                        e.getMessage()
                );

                results.add(result);
            }
        }

        LocalDateTime endTime = LocalDateTime.now();

        UrlDiscoveryResponse response = new UrlDiscoveryResponse(
                totalUrls,
                successfulSources,
                NewsSource.values().length,
                maxUrlsPerSource,
                startTime,
                endTime,
                results
        );

        log.info("URL discovery completed. Total URLs: {}, Successful sources: {}/{}",
                totalUrls, successfulSources, NewsSource.values().length);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/urls/{source}")
    public ResponseEntity<SourceDiscoveryResult> discoverBySource(
            @PathVariable String source,
            @RequestParam(defaultValue = "100") int maxUrls) {

        log.info("Starting URL discovery for source: {}, maxUrls: {}", source, maxUrls);

        try {
            NewsSource newsSource = NewsSource.fromCode(source);
            UrlDiscoveryHandler handler = handlerFactory.createHandler(webDriver, newsSource);
            List<String> urls = handler.discoveredUrls(maxUrls);

            SourceDiscoveryResult result = new SourceDiscoveryResult(
                    newsSource.name(),
                    newsSource.getDisplayName(),
                    newsSource.getBaseUrl(),
                    urls.size(),
                    urls,
                    true,
                    null
            );

            log.info("✅ {} - Found {} URLs", newsSource.getDisplayName(), urls.size());
            urls.forEach(url -> log.debug("   - {}", url));

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid source code: {}", source);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("❌ Error discovering URLs for {}: {}", source, e.getMessage(), e);

            SourceDiscoveryResult result = new SourceDiscoveryResult(
                    source,
                    "Unknown",
                    "Unknown",
                    0,
                    List.of(),
                    false,
                    e.getMessage()
            );

            return ResponseEntity.ok(result);
        }
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> getAvailableSources() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, String>> sources = new ArrayList<>();

        for (NewsSource source : NewsSource.values()) {
            Map<String, String> sourceInfo = new HashMap<>();
            sourceInfo.put("code", source.getCode());
            sourceInfo.put("displayName", source.getDisplayName());
            sourceInfo.put("baseUrl", source.getBaseUrl());
            sources.add(sourceInfo);
        }

        response.put("sources", sources);
        response.put("totalSources", sources.size());

        return ResponseEntity.ok(response);
    }

    @Data
    @AllArgsConstructor
    public static class UrlDiscoveryResponse {
        private int totalUrls;
        private int successfulSources;
        private int totalSources;
        private int maxUrlsPerSource;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private List<SourceDiscoveryResult> results;
    }

    @Data
    @AllArgsConstructor
    public static class SourceDiscoveryResult {
        private String sourceCode;
        private String sourceName;
        private String baseUrl;
        private int urlCount;
        private List<String> urls;
        private boolean success;
        private String error;
    }
}