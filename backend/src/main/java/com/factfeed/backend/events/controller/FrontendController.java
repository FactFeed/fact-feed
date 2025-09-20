package com.factfeed.backend.events.controller;

import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.events.dto.ArticleReferenceDto;
import com.factfeed.backend.events.dto.EventDisplayDto;
import com.factfeed.backend.events.entity.ArticleEventMapping;
import com.factfeed.backend.events.entity.Event;
import com.factfeed.backend.events.repository.ArticleEventMappingRepository;
import com.factfeed.backend.events.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/frontend")
@RequiredArgsConstructor
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class FrontendController {

    private final EventRepository eventRepository;
    private final ArticleEventMappingRepository mappingRepository;

    /**
     * Get processed events for frontend display with pagination
     */
    @GetMapping("/events")
    public ResponseEntity<Map<String, Object>> getFrontendEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "eventDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {

        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();

            Pageable pageable = PageRequest.of(page, size, sort);

            // Get only processed events
            List<Event> allEvents = eventRepository.findByIsProcessed(true);

            // Apply filters
            List<Event> filteredEvents = allEvents.stream()
                    .filter(event -> eventType == null || eventType.isEmpty() ||
                            event.getEventType().equalsIgnoreCase(eventType))
                    .filter(event -> {
                        if (dateFrom == null || dateTo == null) return true;
                        try {
                            LocalDateTime from = LocalDateTime.parse(dateFrom + "T00:00:00");
                            LocalDateTime to = LocalDateTime.parse(dateTo + "T23:59:59");
                            return !event.getEventDate().isBefore(from) && !event.getEventDate().isAfter(to);
                        } catch (Exception e) {
                            return true; // Ignore filter if date parsing fails
                        }
                    })
                    .collect(Collectors.toList());

            // Apply sorting
            if ("eventDate".equals(sortBy)) {
                filteredEvents.sort((e1, e2) -> sortDirection.equalsIgnoreCase("desc")
                        ? e2.getEventDate().compareTo(e1.getEventDate())
                        : e1.getEventDate().compareTo(e2.getEventDate()));
            } else if ("confidenceScore".equals(sortBy)) {
                filteredEvents.sort((e1, e2) -> sortDirection.equalsIgnoreCase("desc")
                        ? e2.getConfidenceScore().compareTo(e1.getConfidenceScore())
                        : e1.getConfidenceScore().compareTo(e2.getConfidenceScore()));
            } else if ("articleCount".equals(sortBy)) {
                filteredEvents.sort((e1, e2) -> sortDirection.equalsIgnoreCase("desc")
                        ? e2.getArticleCount().compareTo(e1.getArticleCount())
                        : e1.getArticleCount().compareTo(e2.getArticleCount()));
            }

            // Apply pagination
            int totalElements = filteredEvents.size();
            int start = page * size;
            int end = Math.min(start + size, totalElements);

            List<Event> pagedEvents = start >= totalElements ?
                    List.of() : filteredEvents.subList(start, end);

            // Convert to DTOs
            List<EventDisplayDto> eventDisplays = pagedEvents.stream()
                    .map(this::convertToDisplayDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "events", eventDisplays,
                    "currentPage", page,
                    "totalPages", (totalElements + size - 1) / size,
                    "totalElements", totalElements,
                    "pageSize", size,
                    "hasNext", end < totalElements,
                    "hasPrevious", page > 0
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching frontend events: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch events",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get detailed event view with all article references
     */
    @GetMapping("/events/{id}")
    public ResponseEntity<EventDisplayDto> getEventDetails(@PathVariable Long id) {
        return eventRepository.findById(id)
                .filter(Event::getIsProcessed) // Only return processed events
                .map(this::convertToDetailedDisplayDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get events by type for frontend
     */
    @GetMapping("/events/by-type/{eventType}")
    public ResponseEntity<List<EventDisplayDto>> getEventsByType(
            @PathVariable String eventType,
            @RequestParam(defaultValue = "20") int limit) {

        List<Event> events = eventRepository.findByEventTypeOrderByEventDateDesc(eventType)
                .stream()
                .filter(Event::getIsProcessed)
                .limit(limit)
                .collect(Collectors.toList());

        List<EventDisplayDto> eventDisplays = events.stream()
                .map(this::convertToDisplayDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(eventDisplays);
    }

    /**
     * Get trending events (high article count and recent)
     */
    @GetMapping("/events/trending")
    public ResponseEntity<List<EventDisplayDto>> getTrendingEvents(
            @RequestParam(defaultValue = "10") int limit) {

        LocalDateTime since = LocalDateTime.now().minusHours(48); // Last 48 hours

        List<Event> trendingEvents = eventRepository.findRecentEvents(since)
                .stream()
                .filter(Event::getIsProcessed)
                .filter(event -> event.getArticleCount() >= 3) // At least 3 articles
                .sorted((e1, e2) -> {
                    // Sort by article count desc, then confidence score desc
                    int countCompare = e2.getArticleCount().compareTo(e1.getArticleCount());
                    return countCompare != 0 ? countCompare :
                            e2.getConfidenceScore().compareTo(e1.getConfidenceScore());
                })
                .limit(limit)
                .collect(Collectors.toList());

        List<EventDisplayDto> eventDisplays = trendingEvents.stream()
                .map(this::convertToDisplayDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(eventDisplays);
    }

    /**
     * Get events with discrepancies
     */
    @GetMapping("/events/with-discrepancies")
    public ResponseEntity<List<EventDisplayDto>> getEventsWithDiscrepancies(
            @RequestParam(defaultValue = "20") int limit) {

        List<Event> events = eventRepository.findByIsProcessed(true)
                .stream()
                .filter(event -> event.getDiscrepancies() != null &&
                        !event.getDiscrepancies().trim().isEmpty() &&
                        !event.getDiscrepancies().contains("কোনো উল্লেখযোগ্য তথ্যগত বিভেদ পাওয়া যায়নি"))
                .sorted((e1, e2) -> e2.getEventDate().compareTo(e1.getEventDate()))
                .limit(limit)
                .collect(Collectors.toList());

        List<EventDisplayDto> eventDisplays = events.stream()
                .map(this::convertToDisplayDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(eventDisplays);
    }

    /**
     * Get articles for a specific event
     */
    @GetMapping("/events/{eventId}/articles")
    public ResponseEntity<List<ArticleReferenceDto>> getEventArticles(@PathVariable Long eventId) {
        return eventRepository.findById(eventId)
                .filter(Event::getIsProcessed)
                .map(event -> {
                    List<ArticleEventMapping> mappings = mappingRepository.findByEventOrderByConfidenceScoreDesc(event);

                    List<ArticleReferenceDto> articleRefs = mappings.stream()
                            .map(mapping -> convertToArticleReference(mapping.getArticle(), mapping.getConfidenceScore()))
                            .collect(Collectors.toList());

                    return ResponseEntity.ok(articleRefs);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get recent events with infinite scroll support
     */
    @GetMapping("/events/recent")
    public ResponseEntity<Map<String, Object>> getRecentEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) Long lastEventId) {

        try {
            List<Event> allEvents = lastEventId != null ?
                    eventRepository.findByIsProcessedAndIdLessThanOrderByEventDateDesc(true, lastEventId) :
                    eventRepository.findByIsProcessedOrderByEventDateDesc(true);

            int start = page * size;
            int end = Math.min(start + size, allEvents.size());

            List<Event> pagedEvents = start >= allEvents.size() ?
                    List.of() : allEvents.subList(start, end);

            List<EventDisplayDto> eventDisplays = pagedEvents.stream()
                    .map(this::convertToDetailedDisplayDto) // Include articles for better UX
                    .collect(Collectors.toList());

            Long nextEventId = eventDisplays.isEmpty() ? null :
                    eventDisplays.get(eventDisplays.size() - 1).getId();

            return ResponseEntity.ok(Map.of(
                    "events", eventDisplays,
                    "hasMore", end < allEvents.size(),
                    "nextEventId", nextEventId != null ? nextEventId : 0,
                    "totalCount", allEvents.size()
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching recent events: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch recent events",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get events by date range with better filtering
     */
    @GetMapping("/events/by-date")
    public ResponseEntity<Map<String, Object>> getEventsByDate(
            @RequestParam String dateFrom,
            @RequestParam String dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        try {
            LocalDateTime from = LocalDateTime.parse(dateFrom + "T00:00:00");
            LocalDateTime to = LocalDateTime.parse(dateTo + "T23:59:59");

            List<Event> events = eventRepository.findByIsProcessedAndEventDateBetweenOrderByEventDateDesc(
                    true, from, to);

            int start = page * size;
            int end = Math.min(start + size, events.size());

            List<Event> pagedEvents = start >= events.size() ?
                    List.of() : events.subList(start, end);

            List<EventDisplayDto> eventDisplays = pagedEvents.stream()
                    .map(this::convertToDetailedDisplayDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "events", eventDisplays,
                    "currentPage", page,
                    "totalPages", (events.size() + size - 1) / size,
                    "totalElements", events.size(),
                    "hasMore", end < events.size()
            ));

        } catch (Exception e) {
            log.error("❌ Error fetching events by date: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to fetch events by date",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Search events with better text matching
     */
    @GetMapping("/events/search")
    public ResponseEntity<Map<String, Object>> searchEvents(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size) {

        try {
            String searchQuery = query.toLowerCase().trim();

            List<Event> allEvents = eventRepository.findByIsProcessed(true)
                    .stream()
                    .filter(event ->
                            event.getTitle().toLowerCase().contains(searchQuery) ||
                                    (event.getAggregatedSummary() != null &&
                                            event.getAggregatedSummary().toLowerCase().contains(searchQuery)) ||
                                    event.getEventType().toLowerCase().contains(searchQuery))
                    .sorted((e1, e2) -> e2.getEventDate().compareTo(e1.getEventDate()))
                    .collect(Collectors.toList());

            int start = page * size;
            int end = Math.min(start + size, allEvents.size());

            List<Event> pagedEvents = start >= allEvents.size() ?
                    List.of() : allEvents.subList(start, end);

            List<EventDisplayDto> eventDisplays = pagedEvents.stream()
                    .map(this::convertToDetailedDisplayDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "events", eventDisplays,
                    "currentPage", page,
                    "totalPages", (allEvents.size() + size - 1) / size,
                    "totalElements", allEvents.size(),
                    "hasMore", end < allEvents.size(),
                    "query", query
            ));

        } catch (Exception e) {
            log.error("❌ Error searching events: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Failed to search events",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get summary statistics for frontend dashboard
     */
    @GetMapping("/dashboard/stats")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        LocalDateTime today = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        LocalDateTime yesterday = today.minusDays(1);
        LocalDateTime weekAgo = today.minusDays(7);

        Long totalEvents = eventRepository.count();
        Long processedEvents = eventRepository.countByIsProcessed(true);
        Long todaysEvents = eventRepository.countEventsSince(today);
        Long weeklyEvents = eventRepository.countEventsSince(weekAgo);

        Double averageConfidence = eventRepository.getAverageConfidenceScore();
        Double averageArticleCount = eventRepository.getAverageArticleCountPerEvent();

        // Events with discrepancies
        long eventsWithDiscrepancies = eventRepository.findByIsProcessed(true)
                .stream()
                .filter(event -> event.getDiscrepancies() != null &&
                        !event.getDiscrepancies().trim().isEmpty() &&
                        !event.getDiscrepancies().contains("কোনো উল্লেখযোগ্য তথ্যগত বিভেদ পাওয়া যায়নি"))
                .count();

        return ResponseEntity.ok(Map.of(
                "totalEvents", totalEvents != null ? totalEvents : 0,
                "processedEvents", processedEvents != null ? processedEvents : 0,
                "todaysEvents", todaysEvents != null ? todaysEvents : 0,
                "weeklyEvents", weeklyEvents != null ? weeklyEvents : 0,
                "averageConfidenceScore", averageConfidence != null ? Math.round(averageConfidence * 100.0) / 100.0 : 0.0,
                "averageArticleCount", averageArticleCount != null ? Math.round(averageArticleCount * 100.0) / 100.0 : 0.0,
                "eventsWithDiscrepancies", eventsWithDiscrepancies,
                "processingPercentage", totalEvents != null && totalEvents > 0 ?
                        Math.round((double) processedEvents / totalEvents * 100) : 0
        ));
    }

    // Private helper methods

    private EventDisplayDto convertToDisplayDto(Event event) {
        return new EventDisplayDto(
                event.getId(),
                event.getTitle(),
                event.getEventType(),
                event.getAggregatedSummary(),
                event.getDiscrepancies(),
                event.getConfidenceScore(),
                event.getArticleCount(),
                event.getEventDate(),
                event.getCreatedAt(),
                hasSignificantDiscrepancies(event.getDiscrepancies()),
                null // Don't load articles in list view for performance
        );
    }

    private EventDisplayDto convertToDetailedDisplayDto(Event event) {
        List<ArticleEventMapping> mappings = mappingRepository.findByEventOrderByConfidenceScoreDesc(event);

        List<ArticleReferenceDto> articles = mappings.stream()
                .map(mapping -> convertToArticleReference(mapping.getArticle(), mapping.getConfidenceScore()))
                .collect(Collectors.toList());

        return new EventDisplayDto(
                event.getId(),
                event.getTitle(),
                event.getEventType(),
                event.getAggregatedSummary(),
                event.getDiscrepancies(),
                event.getConfidenceScore(),
                event.getArticleCount(),
                event.getEventDate(),
                event.getCreatedAt(),
                hasSignificantDiscrepancies(event.getDiscrepancies()),
                articles
        );
    }

    private ArticleReferenceDto convertToArticleReference(Article article, Double confidenceScore) {
        return new ArticleReferenceDto(
                article.getId(),
                article.getTitle(),
                article.getSource().name(),
                article.getUrl(),
                article.getArticlePublishedAt(),
                article.getSummarizedContent(),
                confidenceScore
        );
    }

    private boolean hasSignificantDiscrepancies(String discrepancies) {
        return discrepancies != null &&
                !discrepancies.trim().isEmpty() &&
                !discrepancies.contains("কোনো উল্লেখযোগ্য তথ্যগত বিভেদ পাওয়া যায়নি");
    }
}