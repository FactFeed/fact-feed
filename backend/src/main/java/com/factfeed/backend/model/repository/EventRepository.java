package com.factfeed.backend.model.repository;

import com.factfeed.backend.model.entity.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Find recent events within specified time window
     */
    @Query("SELECT e FROM Event e WHERE e.createdAt >= :fromDate ORDER BY e.createdAt DESC")
    Page<Event> findRecentEvents(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);

    /**
     * Find events by category
     */
    Page<Event> findByCategory(String category, Pageable pageable);

    /**
     * Find events with multiple sources (indicating cross-verification)
     */
    @Query("SELECT e FROM Event e WHERE e.sourceCount > 1 ORDER BY e.sourceCount DESC, e.createdAt DESC")
    Page<Event> findEventsWithMultipleSources(Pageable pageable);

    /**
     * Search events by title or description
     */
    @Query("SELECT e FROM Event e WHERE " +
            "LOWER(e.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Event> searchEvents(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Find events that need aggregation update (no aggregated content or outdated content)
     * <p>
     * Note: Use LEFT JOIN to ensure events without aggregated content are included in results.
     */
    @Query("SELECT e FROM Event e LEFT JOIN e.aggregatedContent ac " +
            "WHERE ac IS NULL OR ac.updatedAt < :beforeDate")
    List<Event> findEventsNeedingAggregation(@Param("beforeDate") LocalDateTime beforeDate);

    /**
     * Get events with article count greater than threshold
     */
    @Query("SELECT e FROM Event e WHERE e.articleCount >= :minArticles ORDER BY e.articleCount DESC")
    Page<Event> findEventsWithMinimumArticles(@Param("minArticles") int minArticles, Pageable pageable);

    /**
     * Find events created in time range
     */
    @Query("SELECT e FROM Event e WHERE e.createdAt BETWEEN :startDate AND :endDate ORDER BY e.createdAt DESC")
    List<Event> findEventsByDateRange(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Count events created today
     */
    @Query("SELECT COUNT(e) FROM Event e WHERE e.createdAt >= :todayStart")
    long countEventsCreatedToday(@Param("todayStart") LocalDateTime todayStart);

    /**
     * Get top events by source diversity (events covered by most sources)
     */
    @Query("SELECT e FROM Event e WHERE e.sourceCount > 1 ORDER BY e.sourceCount DESC, e.articleCount DESC")
    List<Event> findTopEventsBySourceDiversity(Pageable pageable);
}