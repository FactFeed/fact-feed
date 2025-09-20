package com.factfeed.backend.events.controller;

import com.factfeed.backend.events.entity.Event;
import com.factfeed.backend.events.repository.EventRepository;
import com.factfeed.backend.events.service.AggregationService;
import com.factfeed.backend.events.service.EventMappingService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventMappingService eventMappingService;
    private final AggregationService aggregationService;
    private final EventRepository eventRepository;

    /**
     * Trigger event mapping for all unmapped articles
     */
    @PostMapping("/map-all")
    public ResponseEntity<Map<String, Object>> mapAllArticles() {
        log.info("🚀 Starting event mapping for all unmapped articles");

        try {
            String result = eventMappingService.mapAllUnmappedArticles();

            return ResponseEntity.ok(Map.of(
                    "message", result,
                    "status", "COMPLETED",
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("❌ Event mapping failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Event mapping failed",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Trigger aggregation for all unprocessed events
     */
    @PostMapping("/aggregate-all")
    public ResponseEntity<Map<String, Object>> aggregateAllEvents() {
        log.info("🚀 Starting aggregation for all unprocessed events");

        try {
            String result = aggregationService.processAllUnprocessedEvents();

            return ResponseEntity.ok(Map.of(
                    "message", result,
                    "status", "COMPLETED",
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("❌ Event aggregation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Event aggregation failed",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Run complete pipeline: single-batch mapping + aggregation
     */
    @PostMapping("/process-all")
    public ResponseEntity<Map<String, Object>> processCompleteWorkflow() {
        log.info("🚀 Starting complete event processing pipeline");

        try {
            // Step 1: Single-Batch Event Mapping
            log.info("📍 Step 1: Single-Batch Event Mapping");
            String mappingResult = eventMappingService.mapAllUnmappedArticles();

            // Step 2: Event Aggregation with Source-Specific Discrepancy Detection
            log.info("📍 Step 2: Event Aggregation with Source Analysis");
            String aggregationResult = aggregationService.processAllUnprocessedEvents();

            return ResponseEntity.ok(Map.of(
                    "message", "Complete pipeline executed successfully",
                    "mappingResult", mappingResult,
                    "aggregationResult", aggregationResult,
                    "status", "COMPLETED",
                    "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("❌ Complete pipeline failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Complete pipeline failed",
                    "message", e.getMessage(),
                    "timestamp", System.currentTimeMillis()
            ));
        }
    }

    /**
     * Get event statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEventStats() {
        Map<String, Object> mappingStats = eventMappingService.getEventMappingStats();
        Map<String, Object> aggregationStats = aggregationService.getAggregationStats();

        return ResponseEntity.ok(Map.of(
                "mapping", mappingStats,
                "aggregation", aggregationStats,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * Get all events with pagination
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) Boolean isProcessed,
            @RequestParam(required = false) String eventType) {

        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            List<Event> events;
            long totalElements;

            // Apply filters
            if (isProcessed != null && eventType != null) {
                events = eventRepository.findByIsProcessed(isProcessed).stream()
                        .filter(event -> event.getEventType().equalsIgnoreCase(eventType))
                        .skip((long) page * size)
                        .limit(size)
                        .toList();
                totalElements = eventRepository.findByIsProcessed(isProcessed).stream()
                        .filter(event -> event.getEventType().equalsIgnoreCase(eventType))
                        .count();
            } else if (isProcessed != null) {
                events = eventRepository.findByIsProcessed(isProcessed).stream()
                        .skip((long) page * size)
                        .limit(size)
                        .toList();
                totalElements = eventRepository.countByIsProcessed(isProcessed);
            } else if (eventType != null) {
                events = eventRepository.findByEventTypeOrderByEventDateDesc(eventType).stream()
                        .skip((long) page * size)
                        .limit(size)
                        .toList();
                totalElements = eventRepository.findByEventTypeOrderByEventDateDesc(eventType).size();
            } else {
                Page<Event> eventPage = eventRepository.findAll(pageable);
                events = eventPage.getContent();
                totalElements = eventPage.getTotalElements();
            }

            return ResponseEntity.ok(Map.of(
                    "events", events,
                    "currentPage", page,
                    "totalPages", (totalElements + size - 1) / size,
                    "totalElements", totalElements,
                    "pageSize", size
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching events: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch events",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get a specific event by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get events by date range
     */
    @GetMapping("/by-date")
    public ResponseEntity<List<Event>> getEventsByDateRange(
            @RequestParam String startDate,
            @RequestParam String endDate) {

        try {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");

            List<Event> events = eventRepository.findByEventDateBetween(start, end);
            return ResponseEntity.ok(events);

        } catch (Exception e) {
            log.error("❌ Error parsing date range: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent events (last 24 hours)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Event>> getRecentEvents() {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Event> events = eventRepository.findRecentEvents(since);
        return ResponseEntity.ok(events);
    }

    /**
     * Get events with minimum article count
     */
    @GetMapping("/by-article-count")
    public ResponseEntity<List<Event>> getEventsByMinArticleCount(
            @RequestParam(defaultValue = "2") int minCount) {
        List<Event> events = eventRepository.findByMinimumArticleCount(minCount);
        return ResponseEntity.ok(events);
    }

    /**
     * Get events with minimum confidence score
     */
    @GetMapping("/by-confidence")
    public ResponseEntity<List<Event>> getEventsByMinConfidence(
            @RequestParam(defaultValue = "0.5") double minConfidence) {
        List<Event> events = eventRepository.findByMinimumConfidence(minConfidence);
        return ResponseEntity.ok(events);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "HEALTHY",
                "service", "Event Processing Service",
                "timestamp", System.currentTimeMillis()
        ));
    }
}