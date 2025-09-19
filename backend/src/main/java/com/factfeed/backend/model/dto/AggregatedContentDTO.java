package com.factfeed.backend.model.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AggregatedContent entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedContentDTO {
    private Long id;
    private Long eventId;
    private String aggregatedTitle;
    private String aggregatedSummary;
    private String keyPoints;
    private String timeline;
    private Double confidenceScore;
    private Integer sourceCount;
    private Integer totalArticles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String generatedBy;
    private List<DiscrepancyReportDTO> discrepancies;
}