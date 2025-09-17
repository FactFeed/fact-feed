package com.factfeed.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for scraping status responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrapingStatusDTO {
    private String taskId;
    private String status; // PENDING, RUNNING, COMPLETED, FAILED
    private String sourceName;
    private Integer totalUrls;
    private Integer processedUrls;
    private Integer successfulUrls;
    private Integer failedUrls;
    private Double progressPercentage;
    private String startedAt;
    private String completedAt;
    private String error;
    private Double durationSeconds;
}