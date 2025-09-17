package com.factfeed.backend.scraping;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.model.enums.NewsSource;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

/**
 * Service to load and manage news source configurations from YAML
 */
@Service
@Slf4j
public class NewsSourceConfigService {

    private final Map<NewsSource, NewsSourceConfig> sourceConfigs = new HashMap<>();
    @Value("classpath:news-sources.yml")
    private Resource configResource;

    @PostConstruct
    public void loadConfigurations() {
        try (InputStream inputStream = configResource.getInputStream()) {
            Yaml yaml = new Yaml();
            Map<String, Object> data = yaml.load(inputStream);

            @SuppressWarnings("unchecked")
            Map<String, Object> sources = (Map<String, Object>) data.get("sources");

            for (Map.Entry<String, Object> entry : sources.entrySet()) {
                String sourceKey = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceData = (Map<String, Object>) entry.getValue();

                NewsSource newsSource = mapYamlKeyToNewsSource(sourceKey);
                if (newsSource != null) {
                    NewsSourceConfig config = createConfigFromMap(sourceData);
                    sourceConfigs.put(newsSource, config);
                    log.info("Loaded configuration for news source: {}", newsSource);
                } else {
                    log.warn("Unknown news source key in YAML: {}", sourceKey);
                }
            }

            log.info("Successfully loaded {} news source configurations", sourceConfigs.size());

        } catch (Exception e) {
            log.error("Error loading news source configurations", e);
            throw new RuntimeException("Failed to load news source configurations", e);
        }
    }

    /**
     * Get configuration for a specific news source
     */
    public NewsSourceConfig getConfig(NewsSource source) {
        return sourceConfigs.get(source);
    }

    /**
     * Get all loaded configurations
     */
    public Map<NewsSource, NewsSourceConfig> getAllConfigs() {
        return new HashMap<>(sourceConfigs);
    }

    /**
     * Check if a news source has configuration loaded
     */
    public boolean hasConfig(NewsSource source) {
        return sourceConfigs.containsKey(source);
    }

    /**
     * Map YAML key to NewsSource enum
     */
    private NewsSource mapYamlKeyToNewsSource(String yamlKey) {
        switch (yamlKey.toLowerCase()) {
            case "prothomalo":
                return NewsSource.PROTHOMALO;
            case "dailyittefaq":
                return NewsSource.ITTEFAQ;
            default:
                return null;
        }
    }

    /**
     * Create NewsSourceConfig from YAML map data
     */
    @SuppressWarnings("unchecked")
    private NewsSourceConfig createConfigFromMap(Map<String, Object> sourceData) {
        NewsSourceConfig config = new NewsSourceConfig();

        config.setName((String) sourceData.get("name"));
        config.setBaseUrl((String) sourceData.get("baseUrl"));

        config.setTitleSelectors((List<String>) sourceData.get("titleSelectors"));
        config.setAuthorSelectors((List<String>) sourceData.get("authorSelectors"));
        config.setAuthorLocationSelectors((List<String>) sourceData.get("authorLocationSelectors"));
        config.setPublishedTimeSelectors((List<String>) sourceData.get("publishedTimeSelectors"));
        config.setUpdatedTimeSelectors((List<String>) sourceData.get("updatedTimeSelectors"));
        config.setImageSelectors((List<String>) sourceData.get("imageSelectors"));
        config.setImageCaptionSelectors((List<String>) sourceData.get("imageCaptionSelectors"));
        config.setContentSelectors((List<String>) sourceData.get("contentSelectors"));
        config.setContentIgnoreSelectors((List<String>) sourceData.get("contentIgnoreSelectors"));
        config.setCategorySelectors((List<String>) sourceData.get("categorySelectors"));
        config.setKeywordSelectors((List<String>) sourceData.get("keywordSelectors"));

        config.setCategories((List<String>) sourceData.get("categories"));
        config.setCategoriesToIgnore((List<String>) sourceData.get("categoriesToIgnore"));

        return config;
    }
}