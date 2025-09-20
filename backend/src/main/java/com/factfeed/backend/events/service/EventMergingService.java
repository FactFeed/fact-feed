package com.factfeed.backend.events.service;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.ai.service.ApiUsageMonitoringService;
import com.factfeed.backend.events.dto.EventForMergeAnalysis;
import com.factfeed.backend.events.dto.EventMergeCandidate;
import com.factfeed.backend.events.entity.ArticleEventMapping;
import com.factfeed.backend.events.entity.Event;
import com.factfeed.backend.events.repository.ArticleEventMappingRepository;
import com.factfeed.backend.events.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventMergingService {

    private static final String EVENT_MERGING_PROMPT = """
            আপনি একজন বিশেষজ্ঞ সংবাদ বিশ্লেষক। নিচে দেওয়া ইভেন্টগুলো বিশ্লেষণ করে একই বাস্তব ঘটনা সম্পর্কিত ইভেন্টগুলো চিহ্নিত করুন এবং সেগুলো একত্রিত করার পরামর্শ দিন।
            
            **নির্দেশনা:**
            1. একই বাস্তব ঘটনা সম্পর্কিত ইভেন্টগুলো চিহ্নিত করুন
            2. এই ইভেন্টগুলোর জন্য একটি উন্নত, সামগ্রিক শিরোনাম প্রস্তাব করুন
            3. সবচেয়ে উপযুক্ত ইভেন্ট টাইপ নির্ধারণ করুন
            4. কেন এই ইভেন্টগুলো একত্রিত করা উচিত তার যুক্তিসঙ্গত কারণ দিন
            5. ০.০ থেকে ১.০ পর্যন্ত confidence score দিন (১.০ = নিশ্চিতভাবে একই ঘটনা)
            6. শুধুমাত্র ০.৭+ confidence score থাকলেই merge করার পরামর্শ দিন
            7. কমপক্ষে ২টি ইভেন্ট থাকলেই একটি merge group তৈরি করুন
            
            **বিবেচ্য বিষয়:**
            - সময়ের নৈকট্য (একই দিন বা কাছাকাছি সময়ের ইভেন্ট)
            - বিষয়বস্তুর সাদৃশ্য (একই ব্যক্তি, স্থান, বিষয়)
            - ইভেন্ট টাইপের মিল
            - শিরোনামের অর্থগত সাদৃশ্য
            
            **Input Events:** {inputJson}
            
            অবশ্যই এই exact JSON format এ উত্তর দিন:
            [
              {{
                "eventIds": [1, 3, 5],
                "mergedTitle": "উন্নত একীভূত শিরোনাম",
                "mergedEventType": "চূড়ান্ত ইভেন্ট টাইপ",
                "confidenceScore": 0.85,
                "reasoning": "কেন এই ইভেন্টগুলো একত্রিত করা উচিত তার বিস্তারিত কারণ"
              }},
              {{
                "eventIds": [2, 4],
                "mergedTitle": "আরেকটি একীভূত শিরোনাম",
                "mergedEventType": "ইভেন্ট টাইপ",
                "confidenceScore": 0.92,
                "reasoning": "একত্রিতকরণের যৌক্তিক কারণ"
              }}
            ]
            
            যদি কোনো ইভেন্ট একত্রিত করার যোগ্য না হয়, তাহলে খালি array [] রিটার্ন করুন।
            """;

    private final ChatModel chatModel;
    private final EventRepository eventRepository;
    private final ArticleEventMappingRepository mappingRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final ApiUsageMonitoringService monitoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // API keys for rotation
    @Value("${secret.api.key1}")
    private String apiKey1;
    @Value("${secret.api.key2}")
    private String apiKey2;
    @Value("${secret.api.key3}")
    private String apiKey3;
    @Value("${secret.api.key4}")
    private String apiKey4;
    @Value("${secret.api.key5}")
    private String apiKey5;
    @Value("${secret.api.key6}")
    private String apiKey6;

    /**
     * Identify and merge similar events created in different batches
     */
    @Transactional
    public String mergeAllSimilarEvents() {
        return mergeRecentEvents(48); // Merge events from last 48 hours
    }

    /**
     * Merge recent events within specified hours
     */
    @Transactional
    public String mergeRecentEvents(int hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<Event> recentEvents = eventRepository.findByCreatedAtAfterAndIsProcessed(since, false);

        log.info("🔍 Found {} recent unprocessed events for merge analysis", recentEvents.size());

        if (recentEvents.size() < 2) {
            return String.format("সাম্প্রতিক %d ঘন্টায় কোনো merge করার মতো ইভেন্ট পাওয়া যায়নি", hoursBack);
        }

        return processMergeCandidates(recentEvents);
    }

    /**
     * Process a list of events to identify merge candidates
     */
    @Transactional
    public String processMergeCandidates(List<Event> events) {
        String apiKeyName = getNextApiKey();
        log.info("🤖 Analyzing {} events for merging using key: {}", events.size(), apiKeyName);

        try {
            // Convert events to analysis format
            List<EventForMergeAnalysis> analysisEvents = events.stream()
                    .map(this::convertToAnalysisFormat)
                    .collect(Collectors.toList());

            // Create input JSON
            String inputJson = objectMapper.writeValueAsString(analysisEvents);
            int tokenCount = estimateTokenCount(inputJson);

            log.debug("📝 Event merge analysis input created, estimated tokens: {}", tokenCount);

            // Create prompt and call AI
            PromptTemplate promptTemplate = new PromptTemplate(EVENT_MERGING_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("inputJson", inputJson));

            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getText().trim();

            log.debug("🤖 AI Event Merge Response received: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            // Parse AI response into merge candidates
            List<EventMergeCandidate> mergeCandidates = parseMergeResponse(aiResponse);

            // Execute merges
            int mergedEventCount = executeMerges(mergeCandidates, events);

            // Log successful API usage
            logApiUsage(apiKeyName, (long) events.size(), tokenCount, true, null);

            String result = String.format("ইভেন্ট একত্রিতকরণ সম্পন্ন - %d গ্রুপে %d টি ইভেন্ট merge করা হয়েছে",
                    mergeCandidates.size(), mergedEventCount);
            log.info("✅ Event merging completed: {}", result);
            return result;

        } catch (Exception e) {
            log.error("❌ Error in event merging: {}", e.getMessage());
            logApiUsage(apiKeyName, (long) events.size(), estimateTokenCount("events"), false, e.getMessage());
            throw new RuntimeException("ইভেন্ট একত্রিতকরণ ব্যর্থ: " + e.getMessage(), e);
        }
    }

    /**
     * Get event merging statistics
     */
    public Map<String, Object> getMergeStats() {
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        LocalDateTime since7d = LocalDateTime.now().minusDays(7);

        Long recentUnprocessedEvents = eventRepository.countByCreatedAtAfterAndIsProcessed(since24h, false);
        Long weeklyUnprocessedEvents = eventRepository.countByCreatedAtAfterAndIsProcessed(since7d, false);
        Long totalUnprocessedEvents = eventRepository.countByIsProcessed(false);

        return Map.of(
                "recentUnprocessedEvents24h", recentUnprocessedEvents != null ? recentUnprocessedEvents : 0,
                "weeklyUnprocessedEvents", weeklyUnprocessedEvents != null ? weeklyUnprocessedEvents : 0,
                "totalUnprocessedEvents", totalUnprocessedEvents != null ? totalUnprocessedEvents : 0,
                "recommendedMergeAnalysis", recentUnprocessedEvents != null && recentUnprocessedEvents > 1
        );
    }

    // Private helper methods

    private String getNextApiKey() {
        return monitoringService.getNextAvailableApiKey("EVENT_MERGING");
    }

    private EventForMergeAnalysis convertToAnalysisFormat(Event event) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return new EventForMergeAnalysis(
                event.getId(),
                event.getTitle(),
                event.getEventType(),
                event.getArticleCount(),
                event.getConfidenceScore(),
                event.getEventDate() != null ? event.getEventDate().format(formatter) : ""
        );
    }

    private List<EventMergeCandidate> parseMergeResponse(String aiResponse) throws JsonProcessingException {
        // Clean response - remove markdown if present
        String cleanedResponse = aiResponse;
        if (cleanedResponse.contains("```json")) {
            cleanedResponse = cleanedResponse.substring(cleanedResponse.indexOf("```json") + 7);
        }
        if (cleanedResponse.contains("```")) {
            cleanedResponse = cleanedResponse.substring(0, cleanedResponse.lastIndexOf("```"));
        }
        cleanedResponse = cleanedResponse.trim();

        try {
            JsonNode jsonArray = objectMapper.readTree(cleanedResponse);
            List<EventMergeCandidate> candidates = new ArrayList<>();

            for (JsonNode candidateNode : jsonArray) {
                EventMergeCandidate candidate = new EventMergeCandidate();
                candidate.setMergedTitle(candidateNode.get("mergedTitle").asText());
                candidate.setMergedEventType(candidateNode.get("mergedEventType").asText());
                candidate.setConfidenceScore(candidateNode.get("confidenceScore").asDouble());
                candidate.setReasoning(candidateNode.get("reasoning").asText());

                // Parse event IDs
                List<Long> eventIds = new ArrayList<>();
                JsonNode idsArray = candidateNode.get("eventIds");
                for (JsonNode idNode : idsArray) {
                    eventIds.add(idNode.asLong());
                }
                candidate.setEventIds(eventIds);

                // Only include high-confidence merges
                if (candidate.getConfidenceScore() >= 0.7) {
                    candidates.add(candidate);
                }
            }

            return candidates;

        } catch (Exception e) {
            log.error("Failed to parse AI event merge response: {}", cleanedResponse);
            throw new RuntimeException("AI event merge response parsing failed: " + e.getMessage());
        }
    }

    private int executeMerges(List<EventMergeCandidate> candidates, List<Event> allEvents) {
        int mergedEventCount = 0;

        // Create a map for quick event lookup
        Map<Long, Event> eventMap = allEvents.stream()
                .collect(Collectors.toMap(Event::getId, event -> event));

        for (EventMergeCandidate candidate : candidates) {
            try {
                if (candidate.getEventIds().size() < 2) {
                    log.warn("⚠️ Skipping merge candidate with less than 2 events");
                    continue;
                }

                log.info("🔄 Merging {} events: {}", candidate.getEventIds().size(), candidate.getMergedTitle());

                // Find the primary event (first one) to keep
                Long primaryEventId = candidate.getEventIds().get(0);
                Event primaryEvent = eventMap.get(primaryEventId);

                if (primaryEvent == null) {
                    log.warn("⚠️ Primary event with ID {} not found", primaryEventId);
                    continue;
                }

                // Get events to merge into primary
                List<Event> eventsToMerge = candidate.getEventIds().subList(1, candidate.getEventIds().size())
                        .stream()
                        .map(eventMap::get)
                        .filter(event -> event != null)
                        .collect(Collectors.toList());

                // Merge events
                mergeEventsIntoPrimary(primaryEvent, eventsToMerge, candidate);
                mergedEventCount += eventsToMerge.size();

                log.info("✅ Successfully merged {} events into '{}'",
                        eventsToMerge.size() + 1, primaryEvent.getTitle());

            } catch (Exception e) {
                log.error("❌ Error executing merge for candidate: {}", e.getMessage());
            }
        }

        return mergedEventCount;
    }

    private void mergeEventsIntoPrimary(Event primaryEvent, List<Event> eventsToMerge, EventMergeCandidate candidate) {
        // Update primary event with merged information
        primaryEvent.setTitle(candidate.getMergedTitle());
        primaryEvent.setEventType(candidate.getMergedEventType());

        // Calculate new article count and confidence
        int totalArticles = primaryEvent.getArticleCount();
        double totalConfidence = primaryEvent.getConfidenceScore() * primaryEvent.getArticleCount();

        for (Event eventToMerge : eventsToMerge) {
            totalArticles += eventToMerge.getArticleCount();
            totalConfidence += eventToMerge.getConfidenceScore() * eventToMerge.getArticleCount();

            // Move all article mappings to primary event
            List<ArticleEventMapping> mappings = mappingRepository.findByEvent(eventToMerge);
            for (ArticleEventMapping mapping : mappings) {
                mapping.setEvent(primaryEvent);
                mapping.setMappingMethod("AI_MERGING");
                mappingRepository.save(mapping);
            }

            // Delete the merged event
            eventRepository.delete(eventToMerge);
        }

        // Update primary event with new totals
        primaryEvent.setArticleCount(totalArticles);
        primaryEvent.setConfidenceScore(totalConfidence / totalArticles); // Weighted average
        primaryEvent.setUpdatedAt(LocalDateTime.now());

        eventRepository.save(primaryEvent);
    }

    private void logApiUsage(String apiKeyName, Long eventCount, Integer tokenCount, Boolean success, String errorMessage) {
        try {
            ApiUsageLog logEntry = ApiUsageLog.builder()
                    .apiKeyName(apiKeyName)
                    .operation("EVENT_MERGING")
                    .articleId(eventCount) // Using this field to store event count for merge operations
                    .tokenCount(tokenCount)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

            apiUsageLogRepository.save(logEntry);

        } catch (Exception e) {
            log.error("Failed to log API usage: {}", e.getMessage());
        }
    }

    private int estimateTokenCount(String text) {
        // Rough estimation: 1 token ≈ 4 characters for Bangla text
        return Math.max(1, text.length() / 4);
    }
}