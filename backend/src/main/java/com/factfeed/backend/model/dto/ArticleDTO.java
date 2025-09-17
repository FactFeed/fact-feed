package com.factfeed.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleDTO {
    private Long id;
    private String url;
    private String title;
    private String author;
    private String authorLocation;
    private String publishedAt;
    private String updatedAt;
    private String imageUrl;
    private String imageCaption;
    private String content;
    private String category;
    private String tags;
}