package com.factfeed.backend.article;

import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import java.time.LocalDateTime;
import java.util.List;
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

    @Query("SELECT a.url FROM Article a WHERE a.url IN :urls")
    List<String> findExistingUrls(@Param("urls") List<String> urls);

    Page<Article> findBySource(NewsSource source, Pageable pageable);

    Page<Article> findByCategory(String category, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.publishedAt >= :fromDate AND a.publishedAt IS NOT NULL ORDER BY a.publishedAt DESC")
    Page<Article> findRecentArticles(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);

    @Query("SELECT new com.factfeed.backend.model.dto.ArticleLightDTO(a.id, a.title, a.content) " +
            "FROM Article a WHERE a.publishedAt >= :fromDate AND a.publishedAt IS NOT NULL " +
            "ORDER BY a.publishedAt DESC")
    List<ArticleLightDTO> findRecentArticlesLight(@Param("fromDate") LocalDateTime fromDate);

    @Query("SELECT new com.factfeed.backend.model.dto.ArticleLightDTO(a.id, a.title, a.content) " +
            "FROM Article a WHERE a.summarizedContent IS NULL AND a.content IS NOT NULL " +
            "ORDER BY a.scrapedAt DESC")
    List<ArticleLightDTO> findUnsummarizedArticles(Pageable pageable);

    @Query("SELECT a FROM Article a " +
            "WHERE (LOWER(a.title) LIKE LOWER(CONCAT('%', TRIM(:keyword), '%')) " +
            "OR LOWER(a.content) LIKE LOWER(CONCAT('%', TRIM(:keyword), '%'))) " +
            "AND LENGTH(TRIM(:keyword)) >= 3")
    Page<Article> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}