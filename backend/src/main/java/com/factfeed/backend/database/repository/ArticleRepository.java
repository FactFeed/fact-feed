package com.factfeed.backend.database.repository;

import com.factfeed.backend.database.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByUrl(String url);

    boolean existsByUrl(String url);

    List<Article> findBySource(NewsSource source);

    Page<Article> findBySource(NewsSource source, Pageable pageable);

    List<Article> findBySourceOrderByExtractedAtDesc(NewsSource source);

    Page<Article> findBySourceOrderByExtractedAtDesc(NewsSource source, Pageable pageable);

    List<Article> findAllByOrderByExtractedAtDesc();

    Page<Article> findAllByOrderByExtractedAtDesc(Pageable pageable);

    @Query("SELECT a FROM Article a WHERE a.extractedAt >= :since ORDER BY a.extractedAt DESC")
    List<Article> findRecentArticles(@Param("since") LocalDateTime since);

    @Query("SELECT a FROM Article a WHERE a.extractedAt >= :since ORDER BY a.extractedAt DESC")
    Page<Article> findRecentArticles(@Param("since") LocalDateTime since, Pageable pageable);

    @Query("SELECT a FROM Article a WHERE " +
           "(LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY a.extractedAt DESC")
    List<Article> searchByTitleOrContent(@Param("query") String query);

    @Query("SELECT a FROM Article a WHERE " +
           "(LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(a.content) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY a.extractedAt DESC")
    Page<Article> searchByTitleOrContent(@Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Article a WHERE a.source = :source")
    long countBySource(@Param("source") NewsSource source);

    @Query("SELECT a.url FROM Article a WHERE a.source = :source")
    Set<String> findUrlsBySource(@Param("source") NewsSource source);

    @Query("SELECT MAX(a.extractedAt) FROM Article a WHERE a.source = :source")
    Optional<LocalDateTime> findLastExtractionTimeBySource(@Param("source") NewsSource source);

    @Query("SELECT a.source as source, COUNT(a) as count FROM Article a GROUP BY a.source")
    List<SourceCount> getArticleCountsBySource();

    interface SourceCount {
        NewsSource getSource();
        Long getCount();
    }
}