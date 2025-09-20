package com.factfeed.backend.events.repository;

import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.events.entity.ArticleEventMapping;
import com.factfeed.backend.events.entity.Event;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleEventMappingRepository extends JpaRepository<ArticleEventMapping, Long> {

    // Find mappings by event
    List<ArticleEventMapping> findByEventOrderByConfidenceScoreDesc(Event event);

    // Find mappings by event (for merging operations)
    List<ArticleEventMapping> findByEvent(Event event);

    // Find mappings by article
    List<ArticleEventMapping> findByArticle(Article article);

    // Check if article is already mapped to an event
    boolean existsByArticle(Article article);

    // Find the best mapping for an article (highest confidence)
    Optional<ArticleEventMapping> findTopByArticleOrderByConfidenceScoreDesc(Article article);

    // Find articles for an event with minimum confidence
    @Query("SELECT aem FROM ArticleEventMapping aem WHERE aem.event = :event AND aem.confidenceScore >= :minConfidence ORDER BY aem.confidenceScore DESC")
    List<ArticleEventMapping> findByEventAndMinConfidence(@Param("event") Event event, @Param("minConfidence") Double minConfidence);

    // Get all articles for an event
    @Query("SELECT aem.article FROM ArticleEventMapping aem WHERE aem.event = :event ORDER BY aem.confidenceScore DESC")
    List<Article> findArticlesByEvent(@Param("event") Event event);

    // Get article count for an event
    Long countByEvent(Event event);

    // Find unmapped articles (articles not in any mapping)
    @Query("SELECT a FROM Article a WHERE a.id NOT IN (SELECT aem.article.id FROM ArticleEventMapping aem) AND a.summarizedContent IS NOT NULL AND a.summarizedContent != ''")
    List<Article> findUnmappedSummarizedArticles();

    // Delete mappings by event (for re-processing)
    void deleteByEvent(Event event);

    // Find mappings by method
    List<ArticleEventMapping> findByMappingMethod(String mappingMethod);

    // Statistics
    @Query("SELECT COUNT(DISTINCT aem.article.id) FROM ArticleEventMapping aem")
    Long countMappedArticles();

    @Query("SELECT COUNT(DISTINCT aem.event.id) FROM ArticleEventMapping aem")
    Long countEventsWithMappings();
}