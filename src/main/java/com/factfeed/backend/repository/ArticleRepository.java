package com.factfeed.backend.repository;

import com.factfeed.backend.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository interface for Article entity operations.
 * Provides database access methods for article management.
 */
@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    
    /**
     * Check if an article with the given URL already exists.
     * Used to prevent duplicate articles during scraping.
     */
    boolean existsByUrl(String url);
    
    /**
     * Find articles by source name.
     */
    List<Article> findBySourceName(String sourceName);
    
    /**
     * Find articles by status.
     */
    List<Article> findByStatus(String status);
    
    /**
     * Find articles scraped after a specific date.
     */
    List<Article> findByScrapedAtAfter(LocalDateTime date);
    
    /**
     * Find articles that need initial summarization (status = 'NEW').
     */
    @Query("SELECT a FROM Article a WHERE a.status = 'NEW' AND a.initialSummary IS NULL")
    List<Article> findArticlesNeedingSummarization();
    
    /**
     * Find articles that have been summarized but not yet clustered.
     */
    @Query("SELECT a FROM Article a WHERE a.status = 'SUMMARIZED' AND a.initialSummary IS NOT NULL")
    List<Article> findArticlesForClustering();
    
    /**
     * Count articles by source name.
     */
    long countBySourceName(String sourceName);
    
    /**
     * Find recent articles from a specific source within the last N days.
     */
    @Query("SELECT a FROM Article a WHERE a.sourceName = :sourceName AND a.scrapedAt >= :cutoffDate ORDER BY a.scrapedAt DESC")
    List<Article> findRecentArticlesBySource(@Param("sourceName") String sourceName, @Param("cutoffDate") LocalDateTime cutoffDate);
}