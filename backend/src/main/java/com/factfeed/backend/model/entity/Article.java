package com.factfeed.backend.model.entity;

import com.factfeed.backend.model.enums.NewsSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 100)
    private String author;

    @Column(length = 100)
    private String authorLocation;

    private LocalDateTime publishedAt;

    private LocalDateTime updatedAt;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Column(length = 500)
    private String imageCaption;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String category;

    @Column(length = 500)
    private String tags;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsSource source;

    private Integer wordCount;

    @CreationTimestamp
    private LocalDateTime scrapedAt;

    @UpdateTimestamp
    private LocalDateTime dbUpdatedAt;
}