package com.factfeed.backend.config;

import com.factfeed.backend.model.NewsSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration class that loads news sources from YAML file.
 * Provides access to configured news sources for scraping.
 */
@Configuration
public class NewsSourceConfig {
    
    private static final Logger log = LoggerFactory.getLogger(NewsSourceConfig.class);
    
    private List<NewsSource> sources = new ArrayList<>();
    
    @PostConstruct
    public void loadSources() {
        try {
            log.info("Loading news sources from bangla-news-sources.yml");
            
            Yaml yaml = new Yaml();
            InputStream inputStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream("bangla-news-sources.yml");
            
            if (inputStream == null) {
                log.error("Could not find bangla-news-sources.yml in classpath");
                return;
            }
            
            Map<String, Object> data = yaml.load(inputStream);
            
            if (data != null && data.containsKey("sources")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sourcesData = (List<Map<String, Object>>) data.get("sources");
                
                this.sources = parseSources(sourcesData);
                log.info("Successfully loaded {} news sources", sources.size());
                
                for (NewsSource source : sources) {
                    log.debug("Loaded source: {} with {} categories", 
                            source.getName(), 
                            source.getCategoryUrls() != null ? source.getCategoryUrls().size() : 0);
                }
            } else {
                log.warn("No sources found in configuration file");
            }
            
        } catch (Exception e) {
            log.error("Failed to load news sources configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get all configured news sources.
     */
    public List<NewsSource> getSources() {
        return sources;
    }
    
    /**
     * Get news sources by type.
     */
    public List<NewsSource> getSourcesByType(String type) {
        return sources.stream()
                .filter(source -> type.equals(source.getType()))
                .toList();
    }
    
    /**
     * Get a specific news source by name.
     */
    public NewsSource getSourceByName(String name) {
        return sources.stream()
                .filter(source -> name.equals(source.getName()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Check if sources are loaded and available.
     */
    public boolean hasSourcesLoaded() {
        return !sources.isEmpty();
    }
    
    /**
     * Parse sources from YAML data structure.
     */
    private List<NewsSource> parseSources(List<Map<String, Object>> sourcesData) {
        List<NewsSource> parsedSources = new ArrayList<>();
        
        for (Map<String, Object> sourceData : sourcesData) {
            try {
                NewsSource source = new NewsSource();
                source.setName((String) sourceData.get("name"));
                source.setType((String) sourceData.get("type"));
                source.setBaseUrl((String) sourceData.get("base_url"));
                source.setArticleSelector((String) sourceData.get("article_selector"));
                source.setTitleSelector((String) sourceData.get("title_selector"));
                source.setContentSelector((String) sourceData.get("content_selector"));
                source.setPublishedSelector((String) sourceData.get("published_selector"));
                
                if (sourceData.containsKey("rate_limit_minutes")) {
                    source.setRateLimitMinutes((Integer) sourceData.get("rate_limit_minutes"));
                }
                
                // Parse category URLs
                if (sourceData.containsKey("category_urls")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> categoryData = (List<Map<String, Object>>) sourceData.get("category_urls");
                    source.setCategoryUrls(parseCategoryUrls(categoryData));
                }
                
                parsedSources.add(source);
            } catch (Exception e) {
                log.error("Failed to parse source: {}", e.getMessage());
            }
        }
        
        return parsedSources;
    }
    
    /**
     * Parse category URLs from YAML data.
     */
    private List<com.factfeed.backend.model.CategoryUrl> parseCategoryUrls(List<Map<String, Object>> categoryData) {
        List<com.factfeed.backend.model.CategoryUrl> categoryUrls = new ArrayList<>();
        
        for (Map<String, Object> catData : categoryData) {
            com.factfeed.backend.model.CategoryUrl categoryUrl = new com.factfeed.backend.model.CategoryUrl();
            categoryUrl.setUrl((String) catData.get("url"));
            categoryUrl.setCategory((String) catData.get("category"));
            categoryUrls.add(categoryUrl);
        }
        
        return categoryUrls;
    }
}