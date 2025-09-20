package com.factfeed.backend.events.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "events")
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String aggregatedSummary;

    @Column(columnDefinition = "TEXT")
    private String discrepancies;

    @Column(nullable = false)
    @Builder.Default
    private Integer articleCount = 0;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isProcessed = false;

    // Confidence score for the event clustering (0.0 to 1.0)
    @Column(nullable = false)
    @Builder.Default
    private Double confidenceScore = 0.0;

    @Column(length = 100)
    private String eventType; // e.g., "politics", "sports", "economy", etc.
}