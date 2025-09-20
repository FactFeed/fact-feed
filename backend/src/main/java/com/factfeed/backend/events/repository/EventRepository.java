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

    // Event merging support queries
    @Query("SELECT e FROM Event e WHERE e.createdAt >= :since AND e.isProcessed = :isProcessed ORDER BY e.createdAt DESC")
    List<Event> findByCreatedAtAfterAndIsProcessed(@Param("since") LocalDateTime since, @Param("isProcessed") Boolean isProcessed);

    // Count events by creation date and processing status
    @Query("SELECT COUNT(e) FROM Event e WHERE e.createdAt >= :since AND e.isProcessed = :isProcessed")
    Long countByCreatedAtAfterAndIsProcessed(@Param("since") LocalDateTime since, @Param("isProcessed") Boolean isProcessed);

    // Find events by type and date range for merge analysis
    @Query("SELECT e FROM Event e WHERE e.eventType = :eventType AND e.createdAt >= :since AND e.isProcessed = false ORDER BY e.createdAt DESC")
    List<Event> findUnprocessedEventsByTypeAndDate(@Param("eventType") String eventType, @Param("since") LocalDateTime since);

    // New methods for improved frontend support
    List<Event> findByIsProcessedOrderByEventDateDesc(Boolean isProcessed);

    List<Event> findByIsProcessedAndIdLessThanOrderByEventDateDesc(Boolean isProcessed, Long id);

    List<Event> findByIsProcessedAndEventDateBetweenOrderByEventDateDesc(
            Boolean isProcessed, LocalDateTime startDate, LocalDateTime endDate);
}