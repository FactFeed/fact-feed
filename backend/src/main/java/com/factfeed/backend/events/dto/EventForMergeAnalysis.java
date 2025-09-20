package com.factfeed.backend.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventForMergeAnalysis {
    private Long id;
    private String title;
    private String eventType;
    private Integer articleCount;
    private Double confidenceScore;
    private String publishedDate; // For temporal proximity analysis
}