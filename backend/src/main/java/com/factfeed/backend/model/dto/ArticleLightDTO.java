package com.factfeed.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for AI processing to minimize token usage
 * Contains only essential fields: id, title, content
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleLightDTO {
    private Long id;
    private String title;
    private String content;

    public ArticleLightDTO(String title, String content) {
        this.title = title;
        this.content = content;
    }
}