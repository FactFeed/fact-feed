package com.factfeed.backend.events.repository;

import com.factfeed.backend.events.entity.Event;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {

    // Find events by processed status
    List<Event> findByIsProcessed(Boolean isProcessed);

    // Find recent events
    @Query("SELECT e FROM Event e WHERE e.createdAt >= :since ORDER BY e.createdAt DESC")
    List<Event> findRecentEvents(@Param("since") LocalDateTime since);

    // Find events by date range
    @Query("SELECT e FROM Event e WHERE e.eventDate BETWEEN :startDate AND :endDate ORDER BY e.eventDate DESC")
    List<Event> findByEventDateBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // Find events by type
    List<Event> findByEventTypeOrderByEventDateDesc(String eventType);

    // Count events by processing status
    Long countByIsProcessed(Boolean isProcessed);

    // Find events with minimum article count
    @Query("SELECT e FROM Event e WHERE e.articleCount >= :minCount ORDER BY e.articleCount DESC")
    List<Event> findByMinimumArticleCount(@Param("minCount") Integer minCount);

    // Find events with confidence score above threshold
    @Query("SELECT e FROM Event e WHERE e.confidenceScore >= :minConfidence ORDER BY e.confidenceScore DESC")
    List<Event> findByMinimumConfidence(@Param("minConfidence") Double minConfidence);

    // Statistics queries
    @Query("SELECT COUNT(e) FROM Event e WHERE e.createdAt >= :since")
    Long countEventsSince(@Param("since") LocalDateTime since);

    @Query("SELECT AVG(e.articleCount) FROM Event e WHERE e.isProcessed = true")
    Double getAverageArticleCountPerEvent();

    @Query("SELECT AVG(e.confidenceScore) FROM Event e WHERE e.isProcessed = true")
    Double getAverageConfidenceScore();
}