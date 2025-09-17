package com.factfeed.backend.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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