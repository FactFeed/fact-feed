package com.factfeed.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "articles")
public class Article {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "title", nullable = false, length = 500)
    private String title;
    
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "url", nullable = false, length = 1000, unique = true)
    private String url;
    
    @Column(name = "source_name", nullable = false, length = 100)
    private String sourceName;
    
    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    
    @Column(name = "scraped_at")
    private LocalDateTime scrapedAt;
    
    @Column(name = "initial_summary", columnDefinition = "TEXT")
    private String initialSummary;
    
    @Column(name = "status", length = 20)
    private String status = "NEW";
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Default constructor
    public Article() {
        this.createdAt = LocalDateTime.now();
        this.scrapedAt = LocalDateTime.now();
    }
    
    // Constructor with essential fields
    public Article(String title, String content, String url, String sourceName) {
        this();
        this.title = title;
        this.content = content;
        this.url = url;
        this.sourceName = sourceName;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getSourceName() {
        return sourceName;
    }
    
    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }
    
    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }
    
    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
    
    public LocalDateTime getScrapedAt() {
        return scrapedAt;
    }
    
    public void setScrapedAt(LocalDateTime scrapedAt) {
        this.scrapedAt = scrapedAt;
    }
    
    public String getInitialSummary() {
        return initialSummary;
    }
    
    public void setInitialSummary(String initialSummary) {
        this.initialSummary = initialSummary;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}