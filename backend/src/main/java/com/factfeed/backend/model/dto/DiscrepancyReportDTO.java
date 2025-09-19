package com.factfeed.backend.model.dto;

import com.factfeed.backend.model.entity.DiscrepancyReport;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for DiscrepancyReport entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyReportDTO {
    private Long id;
    private Long aggregatedContentId;
    private DiscrepancyReport.DiscrepancyType type;
    private String title;
    private String description;
    private String conflictingClaims;
    private String sourcesInvolved;
    private Double severityScore;
    private Double confidenceScore;
    private LocalDateTime detectedAt;
    private String detectedBy;
}