package com.factfeed.backend.scraper;

import com.factfeed.backend.entity.Article;
import com.factfeed.backend.model.CategoryUrl;
import com.factfeed.backend.model.NewsSource;
import com.factfeed.backend.repository.ArticleRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Web scraper specifically designed for Bangla news websites.
 * Implements rate limiting, error handling, and content extraction.
 */
@Component
public class BanglaWebScraper implements Scraper {
    
    private static final Logger log = LoggerFactory.getLogger(BanglaWebScraper.class);
    
    @Autowired
    private ArticleRepository articleRepository;
    
    @Value("${factfeed.scraping.delay-between-requests:2000}")
    private int delayBetweenRequests;
    
    @Value("${factfeed.scraping.timeout:10000}")
    private int timeout;
    
    @Value("${factfeed.scraping.user-agent}")
    private String userAgent;
    
    @Override
    public String getType() {
        return "web_scraping";
    }

    @Override
    public List<Article> scrape(NewsSource source) {
        if (source == null) {
            log.warn("Cannot scrape from null source");
            return new ArrayList<>();
        }
        
        log.info("Starting scraping for source: {}", source.getName());
        List<Article> articles = new ArrayList<>();
        
        if (source.getCategoryUrls() == null || source.getCategoryUrls().isEmpty()) {
            log.warn("No category URLs found for source: {}", source.getName());
            return articles;
        }
        
        for (CategoryUrl categoryUrl : source.getCategoryUrls()) {
            try {
                log.debug("Scraping category: {} from URL: {}", categoryUrl.getCategory(), categoryUrl.getUrl());
                List<Article> categoryArticles = scrapeCategory(source, categoryUrl);
                articles.addAll(categoryArticles);
                
                // Rate limiting between categories
                if (delayBetweenRequests > 0) {
                    Thread.sleep(delayBetweenRequests);
                }
            } catch (Exception e) {
                log.error("Failed to scrape category {} from {}: {}", 
                        categoryUrl.getCategory(), source.getName(), e.getMessage());
            }
        }
        
        log.info("Completed scraping for source: {}. Found {} articles", source.getName(), articles.size());
        return articles;
    }

    private List<Article> scrapeCategory(NewsSource source, CategoryUrl categoryUrl) {
        try {
            Document categoryPage = Jsoup.connect(categoryUrl.getUrl())
                .userAgent(userAgent)
                .timeout(timeout)
                .get();

            Elements articleLinks = categoryPage.select(source.getArticleSelector());
            log.debug("Found {} article links in category: {}", articleLinks.size(), categoryUrl.getCategory());
            
            return articleLinks.stream()
                .limit(10) // Limit per category to avoid overwhelming
                .map(link -> scrapeArticle(source, link))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
                
        } catch (IOException e) {
            log.error("Failed to connect to category page: {} - {}", categoryUrl.getUrl(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private Article scrapeArticle(NewsSource source, Element linkElement) {
        try {
            String articleUrl = linkElement.absUrl("href");
            
            if (articleUrl.isEmpty()) {
                log.warn("Empty article URL found for source: {}", source.getName());
                return null;
            }
            
            // Check if we already have this article
            if (articleRepository.existsByUrl(articleUrl)) {
                log.debug("Article already exists, skipping: {}", articleUrl);
                return null;
            }

            // Add small delay between article requests
            if (delayBetweenRequests > 0) {
                Thread.sleep(500); // Half the delay between articles
            }

            Document articlePage = Jsoup.connect(articleUrl)
                .userAgent(userAgent)
                .timeout(timeout)
                .get();

            String title = extractTitle(articlePage, source.getTitleSelector());
            String content = extractContent(articlePage, source.getContentSelector());
            LocalDateTime publishedAt = extractPublishedDate(articlePage, source.getPublishedSelector());

            if (title.isEmpty() || content.isEmpty()) {
                log.warn("Empty title or content for article: {} (title: {}, content length: {})", 
                        articleUrl, title.length(), content.length());
                return null;
            }

            Article article = new Article();
            article.setTitle(title);
            article.setContent(content);
            article.setUrl(articleUrl);
            article.setSourceName(source.getName());
            article.setPublishedAt(publishedAt);
            article.setScrapedAt(LocalDateTime.now());
            article.setStatus("NEW");
            article.setCreatedAt(LocalDateTime.now());

            log.debug("Successfully scraped article: {} from {}", title, source.getName());
            return article;

        } catch (Exception e) {
            log.error("Failed to scrape article from {}: {}", source.getName(), e.getMessage());
            return null;
        }
    }

    private String extractTitle(Document doc, String selector) {
        try {
            Element titleElement = doc.selectFirst(selector);
            return titleElement != null ? titleElement.text().trim() : "";
        } catch (Exception e) {
            log.warn("Failed to extract title using selector '{}': {}", selector, e.getMessage());
            return "";
        }
    }

    private String extractContent(Document doc, String selector) {
        try {
            Elements contentElements = doc.select(selector);
            return contentElements.stream()
                .map(Element::text)
                .filter(text -> !text.trim().isEmpty())
                .collect(Collectors.joining("\n"))
                .trim();
        } catch (Exception e) {
            log.warn("Failed to extract content using selector '{}': {}", selector, e.getMessage());
            return "";
        }
    }

    private LocalDateTime extractPublishedDate(Document doc, String selector) {
        try {
            Element dateElement = doc.selectFirst(selector);
            if (dateElement != null) {
                String dateText = dateElement.attr("datetime");
                if (dateText.isEmpty()) {
                    dateText = dateElement.text();
                }
                // For now, use current time as fallback
                // TODO: Implement proper Bangla date parsing
                return parseBanglaDate(dateText);
            }
        } catch (Exception e) {
            log.warn("Failed to parse date using selector '{}': {}", selector, e.getMessage());
        }
        return LocalDateTime.now(); // Fallback to current time
    }

    private LocalDateTime parseBanglaDate(String dateText) {
        // TODO: Implement proper Bangla date parsing logic
        // Handle formats like "২৩ সেপ্টেম্বর ২০২৫" or ISO dates
        // For now, return current time as fallback
        try {
            // Try to parse ISO format first
            if (dateText.contains("T") || dateText.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                return LocalDateTime.parse(dateText.substring(0, Math.min(19, dateText.length())));
            }
        } catch (Exception e) {
            log.debug("Could not parse date '{}', using current time", dateText);
        }
        return LocalDateTime.now();
    }
}