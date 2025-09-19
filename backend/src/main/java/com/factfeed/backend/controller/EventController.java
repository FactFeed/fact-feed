package com.factfeed.backend.controller;

import com.factfeed.backend.model.dto.AggregatedContentDTO;
import com.factfeed.backend.model.dto.DiscrepancyReportDTO;
import com.factfeed.backend.model.dto.EventDTO;
import com.factfeed.backend.model.entity.AggregatedContent;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.entity.Event;
import com.factfeed.backend.service.EventService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for frontend to display events, aggregated content, and discrepancies
 */
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {

    private final EventService eventService;

    /**
     * Get all events with pagination
     */
    @GetMapping
    public ResponseEntity<Page<EventDTO>> getAllEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        try {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ?
                    Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            Page<Event> events = eventService.getAllEvents(pageable);
            Page<EventDTO> eventDTOs = events.map(this::convertToEventDTO);

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error retrieving events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent events within specified hours
     */
    @GetMapping("/recent")
    public ResponseEntity<Page<EventDTO>> getRecentEvents(
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Event> events = eventService.getRecentEvents(hours, pageable);
            Page<EventDTO> eventDTOs = events.map(this::convertToEventDTO);

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error retrieving recent events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get events by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<Page<EventDTO>> getEventsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Event> events = eventService.getEventsByCategory(category, pageable);
            Page<EventDTO> eventDTOs = events.map(this::convertToEventDTO);

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error retrieving events by category {}: {}", category, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search events by keyword
     */
    @GetMapping("/search")
    public ResponseEntity<Page<EventDTO>> searchEvents(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        try {
            if (keyword.trim().length() < 3) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Event> events = eventService.searchEvents(keyword, pageable);
            Page<EventDTO> eventDTOs = events.map(this::convertToEventDTO);

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error searching events with keyword {}: {}", keyword, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get events with multiple sources (cross-verified news)
     */
    @GetMapping("/cross-verified")
    public ResponseEntity<Page<EventDTO>> getCrossVerifiedEvents(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sourceCount"));
            Page<Event> events = eventService.getEventsWithMultipleSources(pageable);
            Page<EventDTO> eventDTOs = events.map(this::convertToEventDTO);

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error retrieving cross-verified events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get specific event with full details
     */
    @GetMapping("/{eventId}")
    public ResponseEntity<EventDTO> getEvent(@PathVariable Long eventId) {
        try {
            Optional<Event> eventOpt = eventService.getEventById(eventId);

            if (eventOpt.isPresent()) {
                EventDTO eventDTO = convertToDetailedEventDTO(eventOpt.get());
                return ResponseEntity.ok(eventDTO);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Error retrieving event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get aggregated content for an event
     */
    @GetMapping("/{eventId}/aggregated-content")
    public ResponseEntity<AggregatedContentDTO> getAggregatedContent(@PathVariable Long eventId) {
        try {
            Optional<AggregatedContent> contentOpt = eventService.getAggregatedContentByEventId(eventId);

            if (contentOpt.isPresent()) {
                AggregatedContentDTO dto = convertToAggregatedContentDTO(contentOpt.get());
                return ResponseEntity.ok(dto);
            } else {
                return ResponseEntity.notFound().build();
            }

        } catch (Exception e) {
            log.error("Error retrieving aggregated content for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get discrepancies for an event
     */
    @GetMapping("/{eventId}/discrepancies")
    public ResponseEntity<List<DiscrepancyReportDTO>> getEventDiscrepancies(@PathVariable Long eventId) {
        try {
            List<DiscrepancyReportDTO> discrepancies = eventService.getDiscrepanciesByEventId(eventId);
            return ResponseEntity.ok(discrepancies);

        } catch (Exception e) {
            log.error("Error retrieving discrepancies for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get original articles for an event
     */
    @GetMapping("/{eventId}/articles")
    public ResponseEntity<List<Article>> getEventArticles(@PathVariable Long eventId) {
        try {
            List<Article> articles = eventService.getArticlesByEventId(eventId);
            return ResponseEntity.ok(articles);

        } catch (Exception e) {
            log.error("Error retrieving articles for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get trending events (high source diversity and recent)
     */
    @GetMapping("/trending")
    public ResponseEntity<List<EventDTO>> getTrendingEvents(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {

        try {
            List<Event> events = eventService.getTrendingEvents(limit);
            List<EventDTO> eventDTOs = events.stream()
                    .map(this::convertToEventDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(eventDTOs);

        } catch (Exception e) {
            log.error("Error retrieving trending events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get event statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getEventStatistics() {
        try {
            Map<String, Object> stats = eventService.getEventStatistics();
            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("Error retrieving event statistics: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Convert Event entity to basic EventDTO
     */
    private EventDTO convertToEventDTO(Event event) {
        EventDTO dto = new EventDTO();
        dto.setId(event.getId());
        dto.setTitle(event.getTitle());
        dto.setDescription(event.getDescription());
        dto.setCategory(event.getCategory());
        dto.setKeywords(event.getKeywords());
        dto.setEarliestArticleDate(event.getEarliestArticleDate());
        dto.setLatestArticleDate(event.getLatestArticleDate());
        dto.setArticleCount(event.getArticleCount());
        dto.setSourceCount(event.getSourceCount());
        dto.setCreatedAt(event.getCreatedAt());
        dto.setUpdatedAt(event.getUpdatedAt());

        // Add basic aggregated content info if available
        if (event.getAggregatedContent() != null) {
            dto.setHasAggregatedContent(true);
            dto.setAggregatedTitle(event.getAggregatedContent().getAggregatedTitle());
        }

        return dto;
    }

    /**
     * Convert Event entity to detailed EventDTO with all relationships
     */
    private EventDTO convertToDetailedEventDTO(Event event) {
        EventDTO dto = convertToEventDTO(event);

        // Add article information
        if (event.getArticleMappings() != null) {
            Set<Article> articles = event.getArticles();
            dto.setArticles(articles.stream().collect(Collectors.toList()));
        }

        // Add aggregated content
        if (event.getAggregatedContent() != null) {
            dto.setAggregatedContent(convertToAggregatedContentDTO(event.getAggregatedContent()));
        }

        return dto;
    }

    /**
     * Convert AggregatedContent entity to DTO
     */
    private AggregatedContentDTO convertToAggregatedContentDTO(AggregatedContent content) {
        AggregatedContentDTO dto = new AggregatedContentDTO();
        dto.setId(content.getId());
        dto.setEventId(content.getEvent().getId());
        dto.setAggregatedTitle(content.getAggregatedTitle());
        dto.setAggregatedSummary(content.getAggregatedSummary());
        dto.setKeyPoints(content.getKeyPoints());
        dto.setTimeline(content.getTimeline());
        dto.setConfidenceScore(content.getConfidenceScore());
        dto.setSourceCount(content.getSourceCount());
        dto.setTotalArticles(content.getTotalArticles());
        dto.setCreatedAt(content.getCreatedAt());
        dto.setUpdatedAt(content.getUpdatedAt());
        dto.setGeneratedBy(content.getGeneratedBy());

        // Add discrepancies
        if (content.getDiscrepancies() != null) {
            List<DiscrepancyReportDTO> discrepancies = content.getDiscrepancies().stream()
                    .map(this::convertToDiscrepancyDTO)
                    .collect(Collectors.toList());
            dto.setDiscrepancies(discrepancies);
        }

        return dto;
    }

    /**
     * Convert DiscrepancyReport entity to DTO
     */
    private DiscrepancyReportDTO convertToDiscrepancyDTO(com.factfeed.backend.model.entity.DiscrepancyReport discrepancy) {
        DiscrepancyReportDTO dto = new DiscrepancyReportDTO();
        dto.setId(discrepancy.getId());
        dto.setType(discrepancy.getType());
        dto.setTitle(discrepancy.getTitle());
        dto.setDescription(discrepancy.getDescription());
        dto.setConflictingClaims(discrepancy.getConflictingClaims());
        dto.setSourcesInvolved(discrepancy.getSourcesInvolved());
        dto.setSeverityScore(discrepancy.getSeverityScore());
        dto.setConfidenceScore(discrepancy.getConfidenceScore());
        dto.setDetectedAt(discrepancy.getDetectedAt());
        dto.setDetectedBy(discrepancy.getDetectedBy());
        return dto;
    }
}