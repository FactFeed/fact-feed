package com.factfeed.backend.scraper.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleData {
    private Long id;
    private String url;
    private String title;
    private List<String> authors;
    private String publishedAt;

    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String updatedAt;

    @JsonProperty("image_url")
    private String imageUrl;

    @JsonProperty("image_caption")
    private String imageCaption;

    @JsonProperty("content")
    private String content;

    @JsonProperty("category")
    private String category;

    @JsonProperty("tags")
    private List<String> tags;

    @JsonProperty("source_site")
    private String sourceSite;

    @JsonProperty("extracted_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime extractedAt;

    @JsonProperty("extraction_method")
    private String extractionMethod;

    public void generateId() {
        if (this.url != null) {
            this.id = (long) Math.abs(this.url.hashCode());
        } else {
            this.id = System.currentTimeMillis();
        }
    }

    public ValidationResult isValid() {
        if (title == null || title.trim().isEmpty()) {
            return ValidationResult.failure("Title is required");
        }
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.failure("URL is required");
        }
        if (content == null || content.trim().isEmpty()) {
            return ValidationResult.failure("Content is required");
        }
        if (sourceSite == null || sourceSite.trim().isEmpty()) {
            return ValidationResult.failure("Source site is required");
        }

        return ValidationResult.success();
    }

    public String getContentPreview() {
        if (content == null || content.length() <= 200) {
            return content;
        }
        return content.substring(0, 200) + "...";
    }
}