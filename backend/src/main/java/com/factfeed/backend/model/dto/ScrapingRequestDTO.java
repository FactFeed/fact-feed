package com.factfeed.backend.model.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for scraping requests
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScrapingRequestDTO {
    private List<String> urls;
    private String sourceName;
    private Integer limitPerSource;
    private Boolean async;
    private String category;
}