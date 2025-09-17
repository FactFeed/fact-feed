package com.factfeed.backend.article;

import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    Optional<Article> findByUrl(String url);

    boolean existsByUrl(String url);

    Page<Article> findBySource(NewsSource source, Pageable pageable);

    Page<Article> findByCategory(String category, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.publishedAt >= :fromDate AND a.publishedAt IS NOT NULL ORDER BY a.publishedAt DESC")
    Page<Article> findRecentArticles(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);

    @Query("SELECT a FROM Article a " +
            "WHERE (LOWER(a.title) LIKE LOWER(CONCAT('%', TRIM(:keyword), '%')) " +
            "OR LOWER(a.content) LIKE LOWER(CONCAT('%', TRIM(:keyword), '%'))) " +
            "AND LENGTH(TRIM(:keyword)) >= 3")
    Page<Article> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}