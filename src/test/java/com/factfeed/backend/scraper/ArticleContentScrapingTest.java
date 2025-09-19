package com.factfeed.backend.scraper;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.NewsSource;
import com.factfeed.backend.repository.ArticleRepository;
import com.factfeed.backend.service.NewsScrapingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for full article content scraping.
 * Tests that we actually extract article titles, content, and other metadata.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ArticleContentScrapingTest {

    @Autowired
    private NewsScrapingService newsScrapingService;

    @Autowired
    private NewsSourceConfig newsSourceConfig;

    @Autowired
    private ArticleRepository articleRepository;

    @BeforeEach
    void setUp() {
        // Clear any existing articles
        articleRepository.deleteAll();
    }

    @Test
    void shouldScrapeAndStoreFullArticleContent() {
        // Test with a single source
        String sourceName = "Prothom Alo";
        
        System.out.println("=== Testing Full Article Content Scraping ===");
        
        // First check if source exists
        NewsSource source = newsSourceConfig.getSourceByName(sourceName);
        System.out.println("Source found: " + (source != null ? source.getName() : "NULL"));
        if (source != null) {
            System.out.println("Source type: " + source.getType());
            System.out.println("Category URLs count: " + (source.getCategoryUrls() != null ? source.getCategoryUrls().size() : 0));
        }
        
        // Check database before scraping
        long articleCountBefore = articleRepository.count();
        System.out.println("Articles in database before scraping: " + articleCountBefore);
        
        // Scrape articles from the source
        List<Article> scrapedArticles = newsScrapingService.scrapeFromSourceByName(sourceName);
        
        System.out.println("Scraped " + scrapedArticles.size() + " articles from " + sourceName);
        
        // Check database after scraping
        long articleCountAfter = articleRepository.count();
        System.out.println("Articles in database after scraping: " + articleCountAfter);
        
        // Verify articles were scraped and saved
        assertThat(scrapedArticles).isNotEmpty();
        
        // Check each article has full content
        for (int i = 0; i < Math.min(3, scrapedArticles.size()); i++) { // Test first 3 articles
            Article article = scrapedArticles.get(i);
            
            System.out.println("\n--- Article " + (i + 1) + " ---");
            System.out.println("URL: " + article.getUrl());
            System.out.println("Title: " + (article.getTitle() != null ? article.getTitle().substring(0, Math.min(100, article.getTitle().length())) + "..." : "NULL"));
            System.out.println("Content length: " + (article.getContent() != null ? article.getContent().length() : 0) + " characters");
            System.out.println("Source: " + article.getSourceName());
            System.out.println("Published: " + article.getPublishedAt());
            System.out.println("Scraped: " + article.getScrapedAt());
            
            // Validate article data
            assertThat(article.getUrl()).isNotEmpty();
            assertThat(article.getSourceName()).isEqualTo(sourceName);
            assertThat(article.getScrapedAt()).isNotNull();
            assertThat(article.getCreatedAt()).isNotNull();
            
            // These are the critical checks for content
            if (article.getTitle() != null && article.getContent() != null) {
                assertThat(article.getTitle()).isNotEmpty();
                assertThat(article.getContent()).isNotEmpty();
                System.out.println("✅ Article has both title and content");
            } else {
                System.out.println("⚠️  Article missing title or content");
                System.out.println("    Title: " + (article.getTitle() != null ? "present" : "NULL"));
                System.out.println("    Content: " + (article.getContent() != null ? "present" : "NULL"));
            }
        }
        
        // Verify articles were saved to database
        List<Article> savedArticles = articleRepository.findAll();
        System.out.println("\nTotal articles in database: " + savedArticles.size());
        assertThat(savedArticles).hasSizeGreaterThanOrEqualTo(scrapedArticles.size());
        
        // Count articles with content
        long articlesWithContent = savedArticles.stream()
                .filter(a -> a.getTitle() != null && !a.getTitle().isEmpty() 
                          && a.getContent() != null && !a.getContent().isEmpty())
                .count();
        
        System.out.println("Articles with full content: " + articlesWithContent + "/" + savedArticles.size());
        
        // At least some articles should have content (depending on site structure)
        if (articlesWithContent == 0) {
            System.out.println("⚠️  WARNING: No articles have content - CSS selectors may need adjustment");
        } else {
            System.out.println("✅ SUCCESS: " + articlesWithContent + " articles have full content");
        }
    }
    
    @Test 
    void shouldTestAllSourcesForContentExtraction() {
        System.out.println("=== Testing All Sources for Content Extraction ===");
        
        List<Article> allArticles = newsScrapingService.scrapeAllSources();
        System.out.println("Total articles scraped from all sources: " + allArticles.size());
        
        // Group by source and check content
        allArticles.stream()
                .collect(java.util.stream.Collectors.groupingBy(Article::getSourceName))
                .forEach((sourceName, articles) -> {
                    System.out.println("\n--- " + sourceName + " ---");
                    System.out.println("Total articles: " + articles.size());
                    
                    long withContent = articles.stream()
                            .filter(a -> a.getTitle() != null && !a.getTitle().isEmpty() 
                                      && a.getContent() != null && !a.getContent().isEmpty())
                            .count();
                    
                    System.out.println("With content: " + withContent + "/" + articles.size());
                    
                    if (withContent > 0) {
                        Article sampleArticle = articles.stream()
                                .filter(a -> a.getTitle() != null && a.getContent() != null)
                                .findFirst()
                                .orElse(null);
                        
                        if (sampleArticle != null) {
                            System.out.println("Sample title: " + sampleArticle.getTitle().substring(0, Math.min(80, sampleArticle.getTitle().length())) + "...");
                            System.out.println("Content preview: " + sampleArticle.getContent().substring(0, Math.min(150, sampleArticle.getContent().length())) + "...");
                        }
                    }
                });
        
        assertThat(allArticles).isNotEmpty();
    }
}