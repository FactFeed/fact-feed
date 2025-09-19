package com.factfeed.backend.model.repository;

import com.factfeed.backend.model.entity.EventArticleMapping;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventArticleMappingRepository extends JpaRepository<EventArticleMapping, Long> {

    /**
     * Find all mappings for a specific event
     */
    List<EventArticleMapping> findByEventId(Long eventId);

    /**
     * Find all mappings for a specific article
     */
    List<EventArticleMapping> findByArticleId(Long articleId);

    /**
     * Check if a specific event-article mapping exists
     */
    Optional<EventArticleMapping> findByEventIdAndArticleId(Long eventId, Long articleId);

    /**
     * Find mappings with high relevance scores
     */
    @Query("SELECT eam FROM EventArticleMapping eam WHERE eam.relevanceScore >= :minScore ORDER BY eam.relevanceScore DESC")
    List<EventArticleMapping> findByRelevanceScoreGreaterThanEqual(@Param("minScore") Double minScore);

    /**
     * Find mappings by mapping method
     */
    List<EventArticleMapping> findByMappingMethod(String mappingMethod);

    /**
     * Get article IDs for a specific event
     */
    @Query("SELECT eam.article.id FROM EventArticleMapping eam WHERE eam.event.id = :eventId")
    List<Long> findArticleIdsByEventId(@Param("eventId") Long eventId);

    /**
     * Get event IDs for a specific article
     */
    @Query("SELECT eam.event.id FROM EventArticleMapping eam WHERE eam.article.id = :articleId")
    List<Long> findEventIdsByArticleId(@Param("articleId") Long articleId);

    /**
     * Count mappings for an event
     */
    long countByEventId(Long eventId);

    /**
     * Delete all mappings for an event
     */
    void deleteByEventId(Long eventId);

    /**
     * Delete all mappings for an article
     */
    void deleteByArticleId(Long articleId);
}