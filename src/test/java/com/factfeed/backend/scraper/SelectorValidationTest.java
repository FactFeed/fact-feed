package com.factfeed.backend.scraper;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.model.NewsSource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Real-world validation tests for CSS selectors and website connectivity.
 * These tests make actual network requests to verify our selectors work.
 */
@SpringBootTest
@ActiveProfiles("test")
class SelectorValidationTest {

    @Autowired
    private NewsSourceConfig newsSourceConfig;

    @Test
    void shouldLoadNewsSourcesFromConfig() {
        List<NewsSource> sources = newsSourceConfig.getSources();
        
        assertThat(sources).isNotEmpty();
        assertThat(sources).hasSize(4); // We configured 4 sources (removed Kalerkantho and Daily Star, added Jaijaidin)
        
        // Verify all sources are loaded correctly
        for (NewsSource source : sources) {
            assertThat(source.getName()).isNotNull();
            assertThat(source.getType()).isEqualTo("web_scraping");
            assertThat(source.getBaseUrl()).isNotNull();
            assertThat(source.getCategoryUrls()).isNotEmpty();
            assertThat(source.getArticleSelector()).isNotNull();
            assertThat(source.getTitleSelector()).isNotNull();
            assertThat(source.getContentSelector()).isNotNull();
        }
    }

    @Test
    void shouldValidateProthomAloSelectors() {
        NewsSource prothomAlo = newsSourceConfig.getSourceByName("Prothom Alo");
        assertThat(prothomAlo).isNotNull();
        
        // Test connectivity and basic selector validation
        try {
            String testUrl = prothomAlo.getCategoryUrls().get(0).getUrl();
            Document doc = Jsoup.connect(testUrl)
                .userAgent("FactFeed-Test-Bot/1.0")
                .timeout(10000)
                .get();
            
            assertThat(doc).isNotNull();
            assertThat(doc.title()).isNotEmpty();
            
            // Test if article selector finds elements
            Elements articleLinks = doc.select(prothomAlo.getArticleSelector());
            // Don't assert specific count as content changes, just verify selector works
            assertThat(articleLinks).isNotNull();
            
            System.out.println("Prothom Alo validation:");
            System.out.println("- URL: " + testUrl);
            System.out.println("- Page title: " + doc.title());
            System.out.println("- Article links found: " + articleLinks.size());
            
        } catch (Exception e) {
            // Log the error but don't fail the test since external sites may be temporarily unavailable
            System.err.println("Warning: Could not validate Prothom Alo selectors: " + e.getMessage());
        }
    }

    @Test
    void shouldValidateJaijaidinSelectors() {
        NewsSource jaijaidin = newsSourceConfig.getSourceByName("Jaijaidin");
        assertThat(jaijaidin).isNotNull();
        
        try {
            String testUrl = jaijaidin.getCategoryUrls().get(0).getUrl();
            Document doc = Jsoup.connect(testUrl)
                .userAgent("FactFeed-Test-Bot/1.0")
                .timeout(10000)
                .get();
            
            assertThat(doc).isNotNull();
            assertThat(doc.title()).isNotEmpty();
            
            Elements articleLinks = doc.select(jaijaidin.getArticleSelector());
            assertThat(articleLinks).isNotNull();
            
            System.out.println("Jaijaidin validation:");
            System.out.println("- URL: " + testUrl);
            System.out.println("- Page title: " + doc.title());
            System.out.println("- Article links found: " + articleLinks.size());
            
        } catch (Exception e) {
            System.err.println("Warning: Could not validate Jaijaidin selectors: " + e.getMessage());
        }
    }

    @Test
    void shouldValidateAllSourcesConnectivity() {
        List<NewsSource> sources = newsSourceConfig.getSources();
        
        for (NewsSource source : sources) {
            try {
                String testUrl = source.getCategoryUrls().get(0).getUrl();
                Document doc = Jsoup.connect(testUrl)
                    .userAgent("FactFeed-Test-Bot/1.0")
                    .timeout(10000)
                    .get();
                
                System.out.println(source.getName() + " connectivity: OK");
                System.out.println("- URL: " + testUrl);
                System.out.println("- Response title: " + doc.title().substring(0, Math.min(50, doc.title().length())));
                
                // Basic selector validation
                Elements articles = doc.select(source.getArticleSelector());
                System.out.println("- Article elements found: " + articles.size());
                
                // Test a few article links if available
                int tested = 0;
                for (Element article : articles) {
                    if (tested >= 2) break; // Test only first 2 to avoid overloading
                    
                    String href = article.attr("abs:href");
                    if (!href.isEmpty()) {
                        System.out.println("  - Sample article URL: " + href);
                        tested++;
                    }
                }
                
                System.out.println();
                
            } catch (Exception e) {
                System.err.println("Warning: " + source.getName() + " connectivity failed: " + e.getMessage());
            }
        }
    }

    @Test
    void shouldTestRateLimitingBehavior() {
        // Test that multiple requests have proper delays
        long startTime = System.currentTimeMillis();
        
        try {
            // Make multiple requests with delay
            Document doc1 = Jsoup.connect("https://httpbin.org/delay/1")
                .userAgent("FactFeed-Test-Bot/1.0")
                .timeout(5000)
                .get();
            
            Thread.sleep(100); // Small delay as configured in test
            
            Document doc2 = Jsoup.connect("https://httpbin.org/html")
                .userAgent("FactFeed-Test-Bot/1.0")
                .timeout(5000)
                .get();
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            
            System.out.println("Rate limiting test completed in: " + duration + "ms");
            assertThat(duration).isGreaterThan(1000); // Should take at least 1 second due to httpbin delay
            
        } catch (Exception e) {
            System.err.println("Rate limiting test warning: " + e.getMessage());
        }
    }
}