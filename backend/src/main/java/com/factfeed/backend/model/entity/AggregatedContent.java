package com.factfeed.backend.model.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
 * Entity representing aggregated content for a news event
 */
@Entity
@Table(name = "aggregated_content")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false, length = 500)
    private String aggregatedTitle;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String aggregatedSummary;

    @Column(columnDefinition = "TEXT")
    private String keyPoints;

    @Column(columnDefinition = "TEXT")
    private String timeline;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "source_count")
    private Integer sourceCount;

    @Column(name = "total_articles")
    private Integer totalArticles;

    @OneToMany(mappedBy = "aggregatedContent", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<DiscrepancyReport> discrepancies;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "generated_by", length = 50)
    private String generatedBy; // AI_MODEL_NAME, e.g., "gemini-2.5-flash"

    /**
     * Helper method to check if content needs regeneration
     */
    public boolean needsRegeneration(int maxAgeHours) {
        if (updatedAt == null) return true;
        return updatedAt.isBefore(LocalDateTime.now().minusHours(maxAgeHours));
    }
}