package com.factfeed.backend.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for aggregation and discrepancy detection responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationResponseDTO {
    private boolean success;
    private String error;
    private Long eventId;
    private AggregatedContentDTO aggregatedContent;
    private List<DiscrepancyReportDTO> discrepancies;
    private double processingTimeSeconds;
    private String modelUsed;

    public static AggregationResponseDTO success(Long eventId, AggregatedContentDTO content,
                                                 List<DiscrepancyReportDTO> discrepancies,
                                                 double processingTime, String model) {
        AggregationResponseDTO response = new AggregationResponseDTO();
        response.setSuccess(true);
        response.setEventId(eventId);
        response.setAggregatedContent(content);
        response.setDiscrepancies(discrepancies);
        response.setProcessingTimeSeconds(processingTime);
        response.setModelUsed(model);
        return response;
    }

    public static AggregationResponseDTO failure(Long eventId, String error) {
        AggregationResponseDTO response = new AggregationResponseDTO();
        response.setSuccess(false);
        response.setEventId(eventId);
        response.setError(error);
        return response;
    }
}