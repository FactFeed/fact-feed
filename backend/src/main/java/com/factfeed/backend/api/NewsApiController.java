package com.factfeed.backend.api;

import com.factfeed.backend.model.dto.AggregatedContentDTO;
import com.factfeed.backend.model.dto.DiscrepancyReportDTO;
import com.factfeed.backend.model.dto.EventDTO;
import com.factfeed.backend.model.entity.AggregatedContent;
import com.factfeed.backend.model.entity.DiscrepancyReport;
import com.factfeed.backend.model.entity.Event;
import com.factfeed.backend.model.repository.AggregatedContentRepository;
import com.factfeed.backend.model.repository.DiscrepancyReportRepository;
import com.factfeed.backend.service.EventService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Frontend API controller for displaying news events, aggregated content, and discrepancies
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class NewsApiController {

    private final EventService eventService;
    private final AggregatedContentRepository aggregatedContentRepository;
    private final DiscrepancyReportRepository discrepancyReportRepository;

    /**
     * Get recent news events with pagination
     */
    @GetMapping("/events")
    public ResponseEntity<Page<Event>> getRecentEvents(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Event> events = eventService.getRecentEvents(hours, pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting recent events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get events with multiple sources (cross-verified news)
     */
    @GetMapping("/events/verified")
    public ResponseEntity<Page<Event>> getVerifiedEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Event> events = eventService.getEventsWithMultipleSources(pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error getting verified events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search events
     */
    @GetMapping("/events/search")
    public ResponseEntity<Page<Event>> searchEvents(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            if (q == null || q.trim().length() < 2) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<Event> events = eventService.searchEvents(q.trim(), pageable);
            return ResponseEntity.ok(events);
        } catch (Exception e) {
            log.error("Error searching events: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get event details with aggregated content and discrepancies
     */
    @GetMapping("/events/{eventId}")
    public ResponseEntity<EventDTO> getEventDetails(@PathVariable Long eventId) {
        try {
            Optional<EventDTO> eventOpt = eventService.getEventWithDetails(eventId);
            return eventOpt.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Error getting event details for ID {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get aggregated content for an event
     */
    @GetMapping("/events/{eventId}/aggregated")
    public ResponseEntity<AggregatedContentDTO> getAggregatedContent(@PathVariable Long eventId) {
        try {
            Optional<EventDTO> eventOpt = eventService.getEventWithDetails(eventId);
            if (eventOpt.isPresent() && eventOpt.get().getAggregatedContent() != null) {
                return ResponseEntity.ok(eventOpt.get().getAggregatedContent());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting aggregated content for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get discrepancies for an event
     */
    @GetMapping("/events/{eventId}/discrepancies")
    public ResponseEntity<List<DiscrepancyReportDTO>> getEventDiscrepancies(@PathVariable Long eventId) {
        try {
            Optional<EventDTO> eventOpt = eventService.getEventWithDetails(eventId);
            if (eventOpt.isPresent() && eventOpt.get().getAggregatedContent() != null) {
                return ResponseEntity.ok(eventOpt.get().getAggregatedContent().getDiscrepancies());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("Error getting discrepancies for event {}: {}", eventId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent aggregated content across all events
     */
    @GetMapping("/aggregated")
    public ResponseEntity<Page<AggregatedContentDTO>> getRecentAggregatedContent(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
            Pageable pageable = PageRequest.of(page, size);

            Page<AggregatedContent> content = aggregatedContentRepository.findRecentContent(fromDate, pageable);
            Page<AggregatedContentDTO> contentDTOs = content.map(this::convertToAggregatedContentDTO);

            return ResponseEntity.ok(contentDTOs);
        } catch (Exception e) {
            log.error("Error getting recent aggregated content: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search aggregated content
     */
    @GetMapping("/aggregated/search")
    public ResponseEntity<Page<AggregatedContentDTO>> searchAggregatedContent(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            if (q == null || q.trim().length() < 2) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<AggregatedContent> content = aggregatedContentRepository.searchContent(q.trim(), pageable);
            Page<AggregatedContentDTO> contentDTOs = content.map(this::convertToAggregatedContentDTO);

            return ResponseEntity.ok(contentDTOs);
        } catch (Exception e) {
            log.error("Error searching aggregated content: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get high-confidence aggregated content
     */
    @GetMapping("/aggregated/reliable")
    public ResponseEntity<Page<AggregatedContentDTO>> getReliableContent(
            @RequestParam(defaultValue = "0.8") double minConfidence,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<AggregatedContent> content = aggregatedContentRepository.findHighConfidenceContent(minConfidence, pageable);
            Page<AggregatedContentDTO> contentDTOs = content.map(this::convertToAggregatedContentDTO);

            return ResponseEntity.ok(contentDTOs);
        } catch (Exception e) {
            log.error("Error getting reliable content: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get recent discrepancies across all events
     */
    @GetMapping("/discrepancies")
    public ResponseEntity<Page<DiscrepancyReportDTO>> getRecentDiscrepancies(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
            Pageable pageable = PageRequest.of(page, size);

            Page<DiscrepancyReport> discrepancies = discrepancyReportRepository.findRecentDiscrepancies(fromDate, pageable);
            Page<DiscrepancyReportDTO> discrepancyDTOs = discrepancies.map(this::convertToDiscrepancyDTO);

            return ResponseEntity.ok(discrepancyDTOs);
        } catch (Exception e) {
            log.error("Error getting recent discrepancies: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get critical discrepancies (high severity and confidence)
     */
    @GetMapping("/discrepancies/critical")
    public ResponseEntity<Page<DiscrepancyReportDTO>> getCriticalDiscrepancies(
            @RequestParam(defaultValue = "0.7") double minSeverity,
            @RequestParam(defaultValue = "0.8") double minConfidence,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<DiscrepancyReport> discrepancies = discrepancyReportRepository
                    .findCriticalDiscrepancies(minSeverity, minConfidence, pageable);
            Page<DiscrepancyReportDTO> discrepancyDTOs = discrepancies.map(this::convertToDiscrepancyDTO);

            return ResponseEntity.ok(discrepancyDTOs);
        } catch (Exception e) {
            log.error("Error getting critical discrepancies: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Search discrepancies
     */
    @GetMapping("/discrepancies/search")
    public ResponseEntity<Page<DiscrepancyReportDTO>> searchDiscrepancies(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            if (q == null || q.trim().length() < 2) {
                return ResponseEntity.badRequest().build();
            }

            Pageable pageable = PageRequest.of(page, size);
            Page<DiscrepancyReport> discrepancies = discrepancyReportRepository.searchDiscrepancies(q.trim(), pageable);
            Page<DiscrepancyReportDTO> discrepancyDTOs = discrepancies.map(this::convertToDiscrepancyDTO);

            return ResponseEntity.ok(discrepancyDTOs);
        } catch (Exception e) {
            log.error("Error searching discrepancies: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get discrepancies by type
     */
    @GetMapping("/discrepancies/type/{type}")
    public ResponseEntity<Page<DiscrepancyReportDTO>> getDiscrepanciesByType(
            @PathVariable DiscrepancyReport.DiscrepancyType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<DiscrepancyReport> discrepancies = discrepancyReportRepository.findByType(type, pageable);
            Page<DiscrepancyReportDTO> discrepancyDTOs = discrepancies.map(this::convertToDiscrepancyDTO);

            return ResponseEntity.ok(discrepancyDTOs);
        } catch (Exception e) {
            log.error("Error getting discrepancies by type {}: {}", type, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get news feed (combined events and aggregated content)
     */
    @GetMapping("/feed")
    public ResponseEntity<NewsFeedResponse> getNewsFeed(
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            Pageable pageable = PageRequest.of(page, size);

            // Get recent events
            Page<Event> events = eventService.getRecentEvents(hours, pageable);

            // Get recent aggregated content
            LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
            Page<AggregatedContent> aggregatedContent = aggregatedContentRepository.findRecentContent(fromDate, pageable);

            // Get recent critical discrepancies
            Page<DiscrepancyReport> criticalDiscrepancies = discrepancyReportRepository
                    .findCriticalDiscrepancies(0.7, 0.8, PageRequest.of(0, 5));

            NewsFeedResponse feed = new NewsFeedResponse();
            feed.setEvents(events);
            feed.setAggregatedContent(aggregatedContent.map(this::convertToAggregatedContentDTO));
            feed.setCriticalDiscrepancies(criticalDiscrepancies.map(this::convertToDiscrepancyDTO));
            feed.setTimestamp(LocalDateTime.now());

            return ResponseEntity.ok(feed);

        } catch (Exception e) {
            log.error("Error getting news feed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
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
        return dto;
    }

    /**
     * Convert DiscrepancyReport entity to DTO
     */
    private DiscrepancyReportDTO convertToDiscrepancyDTO(DiscrepancyReport discrepancy) {
        DiscrepancyReportDTO dto = new DiscrepancyReportDTO();
        dto.setId(discrepancy.getId());
        dto.setAggregatedContentId(discrepancy.getAggregatedContent().getId());
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

    /**
     * Response DTO for news feed
     */
    public static class NewsFeedResponse {
        private Page<Event> events;
        private Page<AggregatedContentDTO> aggregatedContent;
        private Page<DiscrepancyReportDTO> criticalDiscrepancies;
        private LocalDateTime timestamp;

        // Getters and setters
        public Page<Event> getEvents() {
            return events;
        }

        public void setEvents(Page<Event> events) {
            this.events = events;
        }

        public Page<AggregatedContentDTO> getAggregatedContent() {
            return aggregatedContent;
        }

        public void setAggregatedContent(Page<AggregatedContentDTO> aggregatedContent) {
            this.aggregatedContent = aggregatedContent;
        }

        public Page<DiscrepancyReportDTO> getCriticalDiscrepancies() {
            return criticalDiscrepancies;
        }

        public void setCriticalDiscrepancies(Page<DiscrepancyReportDTO> criticalDiscrepancies) {
            this.criticalDiscrepancies = criticalDiscrepancies;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }
    }
}