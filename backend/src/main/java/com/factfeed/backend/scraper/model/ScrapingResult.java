package com.factfeed.backend.scraper.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScrapingResult {

    @JsonProperty("site_name")
    private String siteName;

    @JsonProperty("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @JsonProperty("end_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    @JsonProperty("discovered_urls")
    private int discoveredUrls;

    @JsonProperty("processed_urls")
    private int processedUrls;

    @JsonProperty("successful_extractions")
    private int successfulExtractions;

    @JsonProperty("failed_extractions")
    private int failedExtractions;

    @JsonProperty("errors")
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    @JsonProperty("extracted_articles")
    @Builder.Default
    private List<ArticleData> extractedArticles = new ArrayList<>();

    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
    }

    public int getTotalArticles() {
        return successfulExtractions + failedExtractions;
    }

    public double getSuccessRate() {
        int totalAttempts = successfulExtractions + failedExtractions;
        if (totalAttempts == 0) return 0.0;
        return (double) successfulExtractions / totalAttempts;
    }

    public String getSummary() {
        return String.format("%s: %d discovered, %d successful, %d failed (%.1f%% success)",
                siteName, discoveredUrls, successfulExtractions, failedExtractions, getSuccessRate() * 100);
    }

    public boolean isSuccessful() {
        return successfulExtractions > 0 && getSuccessRate() >= 0.7;
    }
}