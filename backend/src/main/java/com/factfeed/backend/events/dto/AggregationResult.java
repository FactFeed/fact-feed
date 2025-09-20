package com.factfeed.backend.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationResult {
    private String aggregatedSummary;
    private String discrepancies;
    private Double confidenceScore;
    private String methodology; // How the aggregation was performed
}