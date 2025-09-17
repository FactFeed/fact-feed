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
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Article {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String url;

    @Column(nullable = false, length = 800)
    private String title;

    @Column(length = 100)
    private String author;

    private LocalDateTime publishedDate;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NewsSource source;

    @Column(length = 50)
    private String category;

    private LocalDateTime scrapedAt;

    private Integer wordCount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}