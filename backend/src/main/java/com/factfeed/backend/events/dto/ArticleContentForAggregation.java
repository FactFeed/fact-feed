package com.factfeed.backend.events.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleContentForAggregation {
    private Long id;
    private String title;
    private String content;
    private String source;
    private String publishedAt;
    private String url;
}