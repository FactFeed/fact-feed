package com.factfeed.backend.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for aggregation and discrepancy detection requests
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregationRequestDTO {
    private Long eventId;
    private List<Long> articleIds;
    private boolean detectDiscrepancies = true;
    private String focusAreas; // Optional: specific areas to focus on
}