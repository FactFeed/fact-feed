package com.factfeed.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDTO {
    private String url;
    private String title;
    private String content;
    private String author;
    private String publishedDate;
    private String imageUrl;
    private String siteName; // Will be mapped to source
    private String category;
    private String language;

    // Helper method to convert publishedDate string to LocalDateTime
    public LocalDateTime getParsedPublishedDate() {
        if (publishedDate != null && !publishedDate.trim().isEmpty()) {
            try {
                return LocalDateTime.parse(publishedDate);
            } catch (Exception e) {
                // If parsing fails, return null and let the service handle it
                return null;
            }
        }
        return null;
    }
}