package com.factfeed.backend.events.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleReferenceDto {
    private Long id;
    private String title;
    private String source;
    private String url;
    private LocalDateTime publishedAt;
    private String summary;
    private Double confidenceScore; // Confidence of this article belonging to the event
}