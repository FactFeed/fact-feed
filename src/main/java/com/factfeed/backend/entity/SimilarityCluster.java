package com.factfeed.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "similarity_clusters")
public class SimilarityCluster {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "article_ids", nullable = false, columnDefinition = "JSONB")
    private String articleIds; // JSON string containing array of article IDs
    
    @Column(name = "final_summary", columnDefinition = "TEXT")
    private String finalSummary;
    
    @Column(name = "key_topics", columnDefinition = "TEXT[]")
    private String keyTopics; // Comma-separated string for simplicity
    
    @Column(name = "status", length = 20)
    private String status = "PENDING";
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // Default constructor
    public SimilarityCluster() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    // Constructor with article IDs
    public SimilarityCluster(String articleIds) {
        this();
        this.articleIds = articleIds;
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getArticleIds() {
        return articleIds;
    }
    
    public void setArticleIds(String articleIds) {
        this.articleIds = articleIds;
    }
    
    public String getFinalSummary() {
        return finalSummary;
    }
    
    public void setFinalSummary(String finalSummary) {
        this.finalSummary = finalSummary;
    }
    
    public String getKeyTopics() {
        return keyTopics;
    }
    
    public void setKeyTopics(String keyTopics) {
        this.keyTopics = keyTopics;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}