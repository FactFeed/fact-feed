package com.factfeed.backend.events.entity;

import com.factfeed.backend.db.entity.Article;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "article_event_mappings")
public class ArticleEventMapping {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    // Confidence score for this article belonging to this event (0.0 to 1.0)
    @Column(nullable = false)
    private Double confidenceScore;

    @CreationTimestamp
    private LocalDateTime mappedAt;

    @Column(length = 50)
    private String mappingMethod; // "AI_CLUSTERING", "MANUAL", etc.
}