package com.factfeed.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Mapping entity between Events and Articles (many-to-many relationship)
 */
@Entity
@Table(name = "event_article_mappings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "article_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventArticleMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(name = "relevance_score")
    private Double relevanceScore;

    @Column(name = "mapping_method", length = 50)
    private String mappingMethod; // AI_CLUSTERING, MANUAL, etc.

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "mapped_at")
    private LocalDateTime mappedAt;

    public EventArticleMapping(Event event, Article article, Double relevanceScore, String mappingMethod) {
        this.event = event;
        this.article = article;
        this.relevanceScore = relevanceScore;
        this.mappingMethod = mappingMethod;
    }
}