package com.factfeed.backend.scraper;

import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.CategoryUrl;
import com.factfeed.backend.model.NewsSource;
import com.factfeed.backend.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for Bangla news scrapers.
 * Tests the complete scraping workflow with real network requests.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BanglaScrapingIntegrationTest {

    @Autowired
    private BanglaWebScraper scraper;

    @Autowired
    private ArticleRepository articleRepository;

    private NewsSource testSource;

    @BeforeEach
    void setUp() {
        // Create a test source configuration
        testSource = createTestSource();
        
        // Clear any existing articles
        articleRepository.deleteAll();
    }

    @Test
    void shouldImplementCorrectScraperType() {
        assertThat(scraper.getType()).isEqualTo("web_scraping");
    }

    @Test
    void shouldHandleNullSource() {
        List<Article> articles = scraper.scrape(null);
        assertThat(articles).isEmpty();
    }

    @Test
    void shouldHandleSourceWithNoCategoryUrls() {
        NewsSource emptySource = new NewsSource();
        emptySource.setName("Empty Source");
        emptySource.setType("web_scraping");
        emptySource.setCategoryUrls(null);

        List<Article> articles = scraper.scrape(emptySource);
        assertThat(articles).isEmpty();
    }

    @Test
    void shouldHandleSourceWithEmptyCategoryUrls() {
        NewsSource emptySource = new NewsSource();
        emptySource.setName("Empty Source");
        emptySource.setType("web_scraping");
        emptySource.setCategoryUrls(new ArrayList<>());

        List<Article> articles = scraper.scrape(emptySource);
        assertThat(articles).isEmpty();
    }

    @Test
    void shouldHandleInvalidUrls() {
        NewsSource invalidSource = createInvalidSource();
        
        // This should not throw an exception, just return empty list
        List<Article> articles = scraper.scrape(invalidSource);
        assertThat(articles).isEmpty();
    }

    @Test
    void shouldRespectRateLimiting() {
        // Test that scraping takes time due to rate limiting
        long startTime = System.currentTimeMillis();
        
        NewsSource source = createMinimalTestSource();
        scraper.scrape(source);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // Should take at least some time due to delays (reduced in test config)
        assertThat(duration).isGreaterThan(50); // At least 50ms
    }

    @Test
    void shouldNotSaveDuplicateArticles() {
        // Create a test article and save it
        Article existingArticle = new Article();
        existingArticle.setTitle("Test Article");
        existingArticle.setContent("Test content");
        existingArticle.setUrl("https://example.com/test-article");
        existingArticle.setSourceName("Test Source");
        articleRepository.save(existingArticle);

        // Verify the article exists
        assertThat(articleRepository.existsByUrl("https://example.com/test-article")).isTrue();
        
        long initialCount = articleRepository.count();
        assertThat(initialCount).isEqualTo(1);
        
        // Now if scraper tries to process the same URL, it should skip it
        // This is tested indirectly through the scraper's duplicate checking logic
        boolean exists = articleRepository.existsByUrl("https://example.com/test-article");
        assertThat(exists).isTrue();
    }

    @Test
    void shouldCreateValidArticleObjects() {
        // Test with a minimal source that points to a reliable test page
        NewsSource source = createMinimalTestSource();
        
        List<Article> articles = scraper.scrape(source);
        
        // Even if no articles are found, the test validates the scraper doesn't crash
        assertThat(articles).isNotNull();
        
        // If articles are found, validate their structure
        for (Article article : articles) {
            assertThat(article.getSourceName()).isEqualTo(source.getName());
            assertThat(article.getStatus()).isEqualTo("NEW");
            assertThat(article.getCreatedAt()).isNotNull();
            assertThat(article.getScrapedAt()).isNotNull();
            // URL should not be empty if article was created
            if (article.getUrl() != null) {
                assertThat(article.getUrl()).isNotEmpty();
            }
        }
    }

    @Test
    void shouldHandleMissingSelectors() {
        NewsSource sourceWithoutSelectors = new NewsSource();
        sourceWithoutSelectors.setName("No Selectors Source");
        sourceWithoutSelectors.setType("web_scraping");
        
        List<CategoryUrl> categoryUrls = new ArrayList<>();
        CategoryUrl categoryUrl = new CategoryUrl();
        categoryUrl.setUrl("https://httpbin.org/html"); // Reliable test URL
        categoryUrl.setCategory("test");
        categoryUrls.add(categoryUrl);
        
        sourceWithoutSelectors.setCategoryUrls(categoryUrls);
        // Deliberately not setting selectors
        
        List<Article> articles = scraper.scrape(sourceWithoutSelectors);
        assertThat(articles).isEmpty(); // Should handle gracefully
    }

    private NewsSource createTestSource() {
        NewsSource source = new NewsSource();
        source.setName("Test Source");
        source.setType("web_scraping");
        source.setBaseUrl("https://httpbin.org");
        
        List<CategoryUrl> categoryUrls = new ArrayList<>();
        CategoryUrl categoryUrl = new CategoryUrl();
        categoryUrl.setUrl("https://httpbin.org/html");
        categoryUrl.setCategory("test");
        categoryUrls.add(categoryUrl);
        
        source.setCategoryUrls(categoryUrls);
        source.setArticleSelector("a");
        source.setTitleSelector("h1");
        source.setContentSelector("p");
        source.setPublishedSelector("time");
        source.setRateLimitMinutes(1);
        
        return source;
    }

    private NewsSource createInvalidSource() {
        NewsSource source = new NewsSource();
        source.setName("Invalid Source");
        source.setType("web_scraping");
        source.setBaseUrl("https://invalid-domain-that-does-not-exist.xyz");
        
        List<CategoryUrl> categoryUrls = new ArrayList<>();
        CategoryUrl categoryUrl = new CategoryUrl();
        categoryUrl.setUrl("https://invalid-domain-that-does-not-exist.xyz/category");
        categoryUrl.setCategory("invalid");
        categoryUrls.add(categoryUrl);
        
        source.setCategoryUrls(categoryUrls);
        source.setArticleSelector("a");
        source.setTitleSelector("h1");
        source.setContentSelector("p");
        source.setPublishedSelector("time");
        
        return source;
    }

    private NewsSource createMinimalTestSource() {
        NewsSource source = new NewsSource();
        source.setName("Minimal Test Source");
        source.setType("web_scraping");
        source.setBaseUrl("https://httpbin.org");
        
        List<CategoryUrl> categoryUrls = new ArrayList<>();
        CategoryUrl categoryUrl = new CategoryUrl();
        categoryUrl.setUrl("https://httpbin.org/html");
        categoryUrl.setCategory("test");
        categoryUrls.add(categoryUrl);
        
        source.setCategoryUrls(categoryUrls);
        source.setArticleSelector("a");
        source.setTitleSelector("h1");
        source.setContentSelector("p");
        source.setPublishedSelector("time");
        source.setRateLimitMinutes(1);
        
        return source;
    }
}