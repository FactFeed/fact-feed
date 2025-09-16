package com.factfeed.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrapingResultDTO {
    private List<ArticleDTO> articles;
    private String siteName;
    private int totalRequested;
    private int totalFound;
    private int totalValid;
    private String scrapedAt;
    private Double durationSeconds;
    private Double successRate;
}