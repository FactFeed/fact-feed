package com.factfeed.backend.model.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for event clustering responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventClusteringResponseDTO {
    private boolean success;
    private String error;
    private List<EventCluster> clusters;
    private int totalArticles;
    private int clusteredArticles;
    private double processingTimeSeconds;

    public static EventClusteringResponseDTO success(List<EventCluster> clusters, int totalArticles, double processingTime) {
        EventClusteringResponseDTO response = new EventClusteringResponseDTO();
        response.setSuccess(true);
        response.setClusters(clusters);
        response.setTotalArticles(totalArticles);
        response.setClusteredArticles(clusters.stream().mapToInt(c -> c.getArticleIds().size()).sum());
        response.setProcessingTimeSeconds(processingTime);
        return response;
    }

    public static EventClusteringResponseDTO failure(String error) {
        EventClusteringResponseDTO response = new EventClusteringResponseDTO();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventCluster {
        private String eventTitle;
        private String eventDescription;
        private List<Long> articleIds;
        private double confidenceScore;
        private String category;
        private Map<String, Object> metadata;
    }
}