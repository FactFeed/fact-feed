package com.factfeed.backend.model;

/**
 * Represents a category URL within a news source.
 */
public class CategoryUrl {
    private String url;
    private String category;
    
    public CategoryUrl() {}
    
    public CategoryUrl(String url, String category) {
        this.url = url;
        this.category = category;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getCategory() {
        return category;
    }
    
    public void setCategory(String category) {
        this.category = category;
    }
    
    @Override
    public String toString() {
        return "CategoryUrl{" +
                "url='" + url + '\'' +
                ", category='" + category + '\'' +
                '}';
    }
}