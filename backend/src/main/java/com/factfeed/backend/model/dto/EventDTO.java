package com.factfeed.backend.model.dto;

import com.factfeed.backend.model.entity.Article;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Event entity
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventDTO {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String keywords;
    private LocalDateTime earliestArticleDate;
    private LocalDateTime latestArticleDate;
    private Integer articleCount;
    private Integer sourceCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Extended fields for detailed view
    private List<Article> articles;
    private AggregatedContentDTO aggregatedContent;

    // Helper fields for frontend
    private boolean hasAggregatedContent = false;
    private String aggregatedTitle;
}