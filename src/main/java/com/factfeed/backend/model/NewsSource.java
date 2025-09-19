package com.factfeed.backend.model;

import java.util.List;

/**
 * Represents a news source configuration loaded from YAML.
 * Contains all the information needed to scrape articles from a specific news site.
 */
public class NewsSource {
    private String name;
    private String type;
    private String baseUrl;
    private List<CategoryUrl> categoryUrls;
    private String articleSelector;
    private String titleSelector;
    private String contentSelector;
    private String publishedSelector;
    private int rateLimitMinutes;
    
    public NewsSource() {}
    
    public NewsSource(String name, String type, String baseUrl) {
        this.name = name;
        this.type = type;
        this.baseUrl = baseUrl;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public List<CategoryUrl> getCategoryUrls() {
        return categoryUrls;
    }
    
    public void setCategoryUrls(List<CategoryUrl> categoryUrls) {
        this.categoryUrls = categoryUrls;
    }
    
    public String getArticleSelector() {
        return articleSelector;
    }
    
    public void setArticleSelector(String articleSelector) {
        this.articleSelector = articleSelector;
    }
    
    public String getTitleSelector() {
        return titleSelector;
    }
    
    public void setTitleSelector(String titleSelector) {
        this.titleSelector = titleSelector;
    }
    
    public String getContentSelector() {
        return contentSelector;
    }
    
    public void setContentSelector(String contentSelector) {
        this.contentSelector = contentSelector;
    }
    
    public String getPublishedSelector() {
        return publishedSelector;
    }
    
    public void setPublishedSelector(String publishedSelector) {
        this.publishedSelector = publishedSelector;
    }
    
    public int getRateLimitMinutes() {
        return rateLimitMinutes;
    }
    
    public void setRateLimitMinutes(int rateLimitMinutes) {
        this.rateLimitMinutes = rateLimitMinutes;
    }
    
    @Override
    public String toString() {
        return "NewsSource{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", categoryUrls=" + categoryUrls +
                ", articleSelector='" + articleSelector + '\'' +
                ", titleSelector='" + titleSelector + '\'' +
                ", contentSelector='" + contentSelector + '\'' +
                ", publishedSelector='" + publishedSelector + '\'' +
                ", rateLimitMinutes=" + rateLimitMinutes +
                '}';
    }
}