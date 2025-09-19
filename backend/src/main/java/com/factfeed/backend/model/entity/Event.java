package com.factfeed.backend.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Entity representing a news event that may be covered by multiple articles
 */
@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String keywords;

    @Column(name = "earliest_article_date")
    private LocalDateTime earliestArticleDate;

    @Column(name = "latest_article_date")
    private LocalDateTime latestArticleDate;

    @Column(name = "article_count")
    private Integer articleCount = 0;

    @Column(name = "source_count")
    private Integer sourceCount = 0;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<EventArticleMapping> articleMappings;

    @OneToOne(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private AggregatedContent aggregatedContent;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Helper method to check if this event is recent
     */
    public boolean isRecent(int hours) {
        return createdAt != null &&
                createdAt.isAfter(LocalDateTime.now().minusHours(hours));
    }

    /**
     * Helper method to get all articles associated with this event
     */
    public Set<Article> getArticles() {
        if (articleMappings == null) return Set.of();
        return articleMappings.stream()
                .map(EventArticleMapping::getArticle)
                .collect(java.util.stream.Collectors.toSet());
    }
}