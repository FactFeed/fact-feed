package com.factfeed.backend.service;

import com.factfeed.backend.ai.EnhancedAIService;
import com.factfeed.backend.article.ArticleRepository;
import com.factfeed.backend.model.dto.AggregatedContentDTO;
import com.factfeed.backend.model.dto.AggregationRequestDTO;
import com.factfeed.backend.model.dto.AggregationResponseDTO;
import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.dto.DiscrepancyReportDTO;
import com.factfeed.backend.model.dto.EventClusteringRequestDTO;
import com.factfeed.backend.model.dto.EventClusteringResponseDTO;
import com.factfeed.backend.model.dto.EventDTO;
import com.factfeed.backend.model.entity.AggregatedContent;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.entity.DiscrepancyReport;
import com.factfeed.backend.model.entity.Event;
import com.factfeed.backend.model.entity.EventArticleMapping;
import com.factfeed.backend.model.repository.AggregatedContentRepository;
import com.factfeed.backend.model.repository.DiscrepancyReportRepository;
import com.factfeed.backend.model.repository.EventArticleMappingRepository;
import com.factfeed.backend.model.repository.EventRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing events and event-article mappings
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EventService {

    private final EventRepository eventRepository;
    private final EventArticleMappingRepository mappingRepository;
    private final AggregatedContentRepository aggregatedContentRepository;
    private final DiscrepancyReportRepository discrepancyRepository;
    private final ArticleRepository articleRepository;
    private final EnhancedAIService enhancedAIService;

    /**
     * Create events from article clustering
     */
    public List<Event> createEventsFromClustering(EventClusteringResponseDTO clusteringResult) {
        log.info("Creating {} events from clustering result", clusteringResult.getClusters().size());

        List<Event> createdEvents = new ArrayList<>();

        for (EventClusteringResponseDTO.EventCluster cluster : clusteringResult.getClusters()) {
            try {
                // Create event entity
                Event event = new Event();
                event.setTitle(cluster.getEventTitle());
                event.setDescription(cluster.getEventDescription());
                event.setCategory(cluster.getCategory());
                event.setArticleCount(cluster.getArticleIds().size());

                // Calculate date range from articles
                List<Article> articles = articleRepository.findAllById(cluster.getArticleIds());
                if (!articles.isEmpty()) {
                    LocalDateTime earliest = articles.stream()
                            .map(Article::getPublishedAt)
                            .filter(date -> date != null)
                            .min(LocalDateTime::compareTo)
                            .orElse(LocalDateTime.now());

                    LocalDateTime latest = articles.stream()
                            .map(Article::getPublishedAt)
                            .filter(date -> date != null)
                            .max(LocalDateTime::compareTo)
                            .orElse(LocalDateTime.now());

                    event.setEarliestArticleDate(earliest);
                    event.setLatestArticleDate(latest);

                    // Count unique sources
                    Set<String> sources = articles.stream()
                            .map(article -> article.getSource().name())
                            .collect(Collectors.toSet());
                    event.setSourceCount(sources.size());
                }

                // Save event
                Event savedEvent = eventRepository.save(event);
                log.info("Created event: {} with {} articles", savedEvent.getTitle(), cluster.getArticleIds().size());

                // Create article mappings
                for (Long articleId : cluster.getArticleIds()) {
                    articleRepository.findById(articleId).ifPresent(article -> {
                        EventArticleMapping mapping = new EventArticleMapping();
                        mapping.setEvent(savedEvent);
                        mapping.setArticle(article);
                        mapping.setRelevanceScore(cluster.getConfidenceScore());
                        mapping.setMappingMethod("AI_CLUSTERING");

                        mappingRepository.save(mapping);
                    });
                }

                createdEvents.add(savedEvent);

            } catch (Exception e) {
                log.error("Error creating event from cluster {}: {}", cluster.getEventTitle(), e.getMessage());
            }
        }

        log.info("Successfully created {} events from clustering", createdEvents.size());
        return createdEvents;
    }

    /**
     * Generate aggregated content for an event
     */
    public AggregationResponseDTO generateAggregatedContent(Long eventId) {
        Optional<Event> eventOpt = eventRepository.findById(eventId);
        if (eventOpt.isEmpty()) {
            return AggregationResponseDTO.failure(eventId, "Event not found");
        }

        Event event = eventOpt.get();

        // Get articles for this event
        List<Long> articleIds = mappingRepository.findArticleIdsByEventId(eventId);
        List<Article> articles = articleRepository.findAllById(articleIds);

        if (articles.isEmpty()) {
            return AggregationResponseDTO.failure(eventId, "No articles found for event");
        }

        log.info("Generating aggregated content for event {} with {} articles", eventId, articles.size());

        // Create aggregation request
        AggregationRequestDTO request = new AggregationRequestDTO();
        request.setEventId(eventId);
        request.setArticleIds(articleIds);
        request.setDetectDiscrepancies(true);

        // Generate aggregated content using AI
        AggregationResponseDTO response = enhancedAIService.generateAggregatedContent(request, articles);

        if (response.isSuccess()) {
            // Save aggregated content to database
            saveAggregatedContent(event, response.getAggregatedContent(), response.getDiscrepancies());
        }

        return response;
    }

    /**
     * Save aggregated content and discrepancies to database
     */
    private void saveAggregatedContent(Event event, AggregatedContentDTO contentDTO,
                                       List<DiscrepancyReportDTO> discrepancyDTOs) {
        try {
            // Delete existing aggregated content if any
            aggregatedContentRepository.findByEventId(event.getId())
                    .ifPresent(existing -> {
                        // Delete associated discrepancies first
                        discrepancyRepository.deleteByAggregatedContentId(existing.getId());
                        aggregatedContentRepository.delete(existing);
                    });

            // Create new aggregated content
            AggregatedContent content = new AggregatedContent();
            content.setEvent(event);
            content.setAggregatedTitle(contentDTO.getAggregatedTitle());
            content.setAggregatedSummary(contentDTO.getAggregatedSummary());
            content.setKeyPoints(contentDTO.getKeyPoints());
            content.setTimeline(contentDTO.getTimeline());
            content.setConfidenceScore(contentDTO.getConfidenceScore());
            content.setSourceCount(contentDTO.getSourceCount());
            content.setTotalArticles(contentDTO.getTotalArticles());
            content.setGeneratedBy(contentDTO.getGeneratedBy());

            AggregatedContent savedContent = aggregatedContentRepository.save(content);
            log.info("Saved aggregated content for event {}", event.getId());

            // Save discrepancies
            for (DiscrepancyReportDTO discrepancyDTO : discrepancyDTOs) {
                DiscrepancyReport discrepancy = new DiscrepancyReport();
                discrepancy.setAggregatedContent(savedContent);
                discrepancy.setType(discrepancyDTO.getType());
                discrepancy.setTitle(discrepancyDTO.getTitle());
                discrepancy.setDescription(discrepancyDTO.getDescription());
                discrepancy.setConflictingClaims(discrepancyDTO.getConflictingClaims());
                discrepancy.setSourcesInvolved(discrepancyDTO.getSourcesInvolved());
                discrepancy.setSeverityScore(discrepancyDTO.getSeverityScore());
                discrepancy.setConfidenceScore(discrepancyDTO.getConfidenceScore());
                discrepancy.setDetectedBy(discrepancyDTO.getDetectedBy());

                discrepancyRepository.save(discrepancy);
            }

            log.info("Saved {} discrepancies for event {}", discrepancyDTOs.size(), event.getId());

        } catch (Exception e) {
            log.error("Error saving aggregated content for event {}: {}", event.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Process recent articles for event clustering
     */
    public EventClusteringResponseDTO processRecentArticlesForEvents(int hours, double similarityThreshold) {
        log.info("Processing articles from last {} hours for event clustering", hours);

        // Get recent articles that haven't been mapped to events yet
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        List<ArticleLightDTO> recentArticles = articleRepository.findRecentArticlesLight(fromDate);

        // Filter out articles already mapped to events
        List<ArticleLightDTO> unmappedArticles = recentArticles.stream()
                .filter(article -> mappingRepository.findByArticleId(article.getId()).isEmpty())
                .collect(Collectors.toList());

        if (unmappedArticles.isEmpty()) {
            log.info("No unmapped articles found for clustering");
            return EventClusteringResponseDTO.success(new ArrayList<>(), 0, 0.0);
        }

        log.info("Found {} unmapped articles for clustering", unmappedArticles.size());

        // Create clustering request
        EventClusteringRequestDTO request = new EventClusteringRequestDTO();
        request.setArticles(unmappedArticles);
        request.setSimilarityThreshold(similarityThreshold);
        request.setMaxClusters(20);

        // Perform clustering
        EventClusteringResponseDTO clusteringResult = enhancedAIService.clusterArticlesIntoEvents(request);

        if (clusteringResult.isSuccess()) {
            // Create events from clustering results
            createEventsFromClustering(clusteringResult);
        }

        return clusteringResult;
    }

    /**
     * Get events that need aggregation update
     */
    public List<Event> getEventsNeedingAggregation(int maxAgeHours) {
        LocalDateTime beforeDate = LocalDateTime.now().minusHours(maxAgeHours);
        return eventRepository.findEventsNeedingAggregation(beforeDate);
    }

    /**
     * Update aggregated content for all events that need it
     */
    public void updateAggregatedContentForEvents(int maxAgeHours) {
        List<Event> eventsNeedingUpdate = getEventsNeedingAggregation(maxAgeHours);

        log.info("Updating aggregated content for {} events", eventsNeedingUpdate.size());

        for (Event event : eventsNeedingUpdate) {
            try {
                AggregationResponseDTO result = generateAggregatedContent(event.getId());
                if (result.isSuccess()) {
                    log.info("Updated aggregated content for event: {}", event.getTitle());
                } else {
                    log.warn("Failed to update aggregated content for event {}: {}",
                            event.getId(), result.getError());
                }

                // Add delay to avoid rate limiting
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("Error updating aggregated content for event {}: {}", event.getId(), e.getMessage());
            }
        }

        log.info("Completed aggregated content updates");
    }

    /**
     * Get recent events with pagination
     */
    public Page<Event> getRecentEvents(int hours, Pageable pageable) {
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        return eventRepository.findRecentEvents(fromDate, pageable);
    }

    /**
     * Search events
     */
    public Page<Event> searchEvents(String keyword, Pageable pageable) {
        return eventRepository.searchEvents(keyword, pageable);
    }

    /**
     * Get event by ID with full details
     */
    public Optional<EventDTO> getEventWithDetails(Long eventId) {
        return eventRepository.findById(eventId)
                .map(this::convertToEventDTO);
    }

    /**
     * Convert Event entity to DTO with full details
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

        // Load aggregated content if available
        aggregatedContentRepository.findByEventId(event.getId())
                .ifPresent(content -> dto.setAggregatedContent(convertToAggregatedContentDTO(content)));

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

        // Load discrepancies
        List<DiscrepancyReport> discrepancies = discrepancyRepository.findByAggregatedContentId(content.getId());
        dto.setDiscrepancies(discrepancies.stream()
                .map(this::convertToDiscrepancyDTO)
                .collect(Collectors.toList()));

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
     * Get events with multiple sources (cross-verified)
     */
    public Page<Event> getEventsWithMultipleSources(Pageable pageable) {
        return eventRepository.findEventsWithMultipleSources(pageable);
    }

    /**
     * Delete event and all associated data
     */
    public void deleteEvent(Long eventId) {
        eventRepository.findById(eventId).ifPresent(event -> {
            log.info("Deleting event: {} and all associated data", event.getTitle());

            // Delete aggregated content and discrepancies (cascade should handle this)
            aggregatedContentRepository.findByEventId(eventId)
                    .ifPresent(content -> {
                        discrepancyRepository.deleteByAggregatedContentId(content.getId());
                        aggregatedContentRepository.delete(content);
                    });

            // Delete event-article mappings
            mappingRepository.deleteByEventId(eventId);

            // Delete event
            eventRepository.delete(event);

            log.info("Deleted event {} and all associated data", eventId);
        });
    }

    /**
     * Get all events with pagination
     */
    public Page<Event> getAllEvents(Pageable pageable) {
        return eventRepository.findAll(pageable);
    }

    /**
     * Get event by ID
     */
    public Optional<Event> getEventById(Long eventId) {
        return eventRepository.findById(eventId);
    }

    /**
     * Get events by category
     */
    public Page<Event> getEventsByCategory(String category, Pageable pageable) {
        return eventRepository.findByCategory(category, pageable);
    }

    /**
     * Get aggregated content by event ID
     */
    public Optional<AggregatedContent> getAggregatedContentByEventId(Long eventId) {
        return aggregatedContentRepository.findByEventId(eventId);
    }

    /**
     * Get discrepancies by event ID
     */
    public List<DiscrepancyReportDTO> getDiscrepanciesByEventId(Long eventId) {
        Optional<AggregatedContent> contentOpt = aggregatedContentRepository.findByEventId(eventId);
        if (contentOpt.isPresent()) {
            return discrepancyRepository.findByAggregatedContentId(contentOpt.get().getId())
                    .stream()
                    .map(this::convertToDiscrepancyDTO)
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    /**
     * Get articles by event ID
     */
    public List<Article> getArticlesByEventId(Long eventId) {
        List<EventArticleMapping> mappings = mappingRepository.findByEventId(eventId);
        return mappings.stream()
                .map(EventArticleMapping::getArticle)
                .collect(Collectors.toList());
    }

    /**
     * Get trending events (high source diversity and recent)
     */
    public List<Event> getTrendingEvents(int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return eventRepository.findTopEventsBySourceDiversity(pageable);
    }

    /**
     * Get event statistics
     */
    public Map<String, Object> getEventStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Total events
        long totalEvents = eventRepository.count();
        stats.put("total_events", totalEvents);

        // Events created today
        LocalDateTime todayStart = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);
        long eventsToday = eventRepository.countEventsCreatedToday(todayStart);
        stats.put("events_today", eventsToday);

        // Events with multiple sources
        Pageable pageable = PageRequest.of(0, 1);
        long crossVerifiedEvents = eventRepository.findEventsWithMultipleSources(pageable).getTotalElements();
        stats.put("cross_verified_events", crossVerifiedEvents);

        // Events with aggregated content
        long eventsWithAggregation = aggregatedContentRepository.count();
        stats.put("events_with_aggregation", eventsWithAggregation);

        // Aggregated content created today
        long contentToday = aggregatedContentRepository.countCreatedSince(todayStart);
        stats.put("aggregations_today", contentToday);

        // Recent events (last 24 hours)
        LocalDateTime oneDayAgo = LocalDateTime.now().minusHours(24);
        Page<Event> recentEvents = eventRepository.findRecentEvents(oneDayAgo, pageable);
        stats.put("recent_events_24h", recentEvents.getTotalElements());

        return stats;
    }
}