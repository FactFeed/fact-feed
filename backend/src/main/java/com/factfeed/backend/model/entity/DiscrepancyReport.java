package com.factfeed.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity representing discrepancies found between articles covering the same event
 */
@Entity
@Table(name = "discrepancy_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregated_content_id", nullable = false)
    private AggregatedContent aggregatedContent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyType type;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String conflictingClaims;

    @Column(columnDefinition = "TEXT")
    private String sourcesInvolved;

    @Column(name = "severity_score")
    private Double severityScore; // 0.0 to 1.0

    @Column(name = "confidence_score")
    private Double confidenceScore; // AI confidence in this discrepancy

    @CreationTimestamp
    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "detected_by", length = 50)
    private String detectedBy; // AI_MODEL_NAME

    public enum DiscrepancyType {
        FACTUAL_CONTRADICTION,
        NUMERICAL_DIFFERENCE,
        TIMELINE_INCONSISTENCY,
        SOURCE_ATTRIBUTION,
        QUOTE_VARIATION,
        INTERPRETATION_DIFFERENCE,
        MISSING_INFORMATION,
        OTHER
    }
}