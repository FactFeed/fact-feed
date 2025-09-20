package com.factfeed.backend.events.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDisplayDto {
    private Long id;
    private String title;
    private String eventType;
    private String aggregatedSummary;
    private String discrepancies;
    private Double confidenceScore;
    private Integer articleCount;
    private LocalDateTime eventDate;
    private LocalDateTime createdAt;
    private Boolean hasDiscrepancies;
    private List<ArticleReferenceDto> articles;
}