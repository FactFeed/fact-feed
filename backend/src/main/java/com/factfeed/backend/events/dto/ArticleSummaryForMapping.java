package com.factfeed.backend.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleSummaryForMapping {
    private Long id;
    private String title;
    private String summary;
    private String publishedAt;
    private String source;
}