package com.factfeed.backend.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for event clustering requests
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventClusteringRequestDTO {
    private List<ArticleLightDTO> articles;
    private double similarityThreshold = 0.7;
    private int maxClusters = 20;
    private String clusteringMethod = "AI_SEMANTIC";
}