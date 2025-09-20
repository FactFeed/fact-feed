package com.factfeed.backend.events.service;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.ai.service.ApiUsageMonitoringService;
import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.events.dto.AggregationResult;
import com.factfeed.backend.events.dto.ArticleContentForAggregation;
import com.factfeed.backend.events.entity.Event;
import com.factfeed.backend.events.repository.ArticleEventMappingRepository;
import com.factfeed.backend.events.repository.EventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
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
public class AggregationService {

    private static final String AGGREGATION_PROMPT = """
            আপনি একজন বিশেষজ্ঞ সংবাদ বিশ্লেষক। নিচে দেওয়া একই ঘটনা সম্পর্কিত বিভিন্ন সংবাদপত্রের নিবন্ধগুলো বিশ্লেষণ করে একটি সামগ্রিক সারাংশ তৈরি করুন এবং তথ্যগত বিভেদ চিহ্নিত করুন।
            
            **কাজ:**
            1. **সামগ্রিক সারাংশ:** সকল নিবন্ধের তথ্য একত্রিত করে একটি পূর্ণাঙ্গ, নিরপেক্ষ ও তথ্যবহুল সারাংশ তৈরি করুন (১০-১৫ বাক্য)
            2. **তথ্যগত বিভেদ:** বিভিন্ন উৎসের মধ্যে কোনো তথ্যগত অসঙ্গতি, পরস্পরবিরোধী তথ্য, বা ভিন্ন দৃষ্টিভঙ্গি থাকলে তা উল্লেখ করুন
            3. **বিশ্বাসযোগ্যতা স্কোর:** ০.০ থেকে ১.০ এর মধ্যে একটি স্কোর দিন (১.০ = সকল তথ্য সামঞ্জস্যপূর্ণ)
            
            **নির্দেশনা:**
            - সকল গুরুত্বপূর্ণ তথ্য, তারিখ, সংখ্যা, নাম অন্তর্ভুক্ত করুন
            - কোনো নির্দিষ্ট সংবাদপত্রের পক্ষপাতিত্ব করবেন না
            - তথ্যগত বিভেদ থাকলে কোন উৎস কী বলেছে তা স্পষ্ট করুন
            - যদি কোনো বিভেদ না থাকে, "কোনো উল্লেখযোগ্য তথ্যগত বিভেদ পাওয়া যায়নি" লিখুন
            
            **Input Articles:** {inputJson}
            
            অবশ্যই এই exact JSON format এ উত্তর দিন:
            {{
              "aggregatedSummary": "সকল তথ্য একত্রিত করে তৈরি সামগ্রিক সারাংশ (১০-১৫ বাক্য)",
              "discrepancies": "তথ্যগত বিভেদের বিস্তারিত বিবরণ বা 'কোনো উল্লেখযোগ্য তথ্যগত বিভেদ পাওয়া যায়নি'",
              "confidenceScore": 0.92,
              "methodology": "কীভাবে এই বিশ্লেষণ করা হয়েছে তার সংক্ষিপ্ত বিবরণ"
            }}
            """;

    private final ChatModel chatModel;
    private final EventRepository eventRepository;
    private final ArticleEventMappingRepository mappingRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
    private final ApiUsageMonitoringService monitoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger keyIndex = new AtomicInteger(0);

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
     * Process all unprocessed events for aggregation
     */
    @Transactional
    public String processAllUnprocessedEvents() {
        List<Event> unprocessedEvents = eventRepository.findByIsProcessed(false);
        log.info("🔍 Found {} unprocessed events for aggregation", unprocessedEvents.size());

        if (unprocessedEvents.isEmpty()) {
            return "কোনো নতুন ইভেন্ট এগ্রিগেশনের প্রয়োজন নেই";
        }

        int processedCount = 0;
        int errorCount = 0;

        for (Event event : unprocessedEvents) {
            try {
                log.info("🔄 Processing event: {} (ID: {})", event.getTitle(), event.getId());

                AggregationResult result = processEventAggregation(event);
                updateEventWithAggregation(event, result);

                processedCount++;
                log.info("✅ Successfully processed event: {}", event.getTitle());

                // Small delay between events to respect rate limits
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("❌ Error processing event {}: {}", event.getId(), e.getMessage());
                errorCount++;
            }
        }

        String result = String.format("এগ্রিগেশন সম্পন্ন - সফল: %d, ব্যর্থ: %d",
                processedCount, errorCount);
        log.info("📈 Event aggregation completed: {}", result);
        return result;
    }

    /**
     * Process a single event for aggregation
     */
    @Transactional
    public AggregationResult processEventAggregation(Event event) {
        String apiKeyName = getNextApiKey();
        log.info("🤖 Processing aggregation for event '{}' using key: {}", event.getTitle(), apiKeyName);

        try {
            // Get all articles for this event
            List<Article> articles = mappingRepository.findArticlesByEvent(event);

            if (articles.size() < 2) {
                throw new RuntimeException("Event must have at least 2 articles for aggregation");
            }

            // Convert articles to aggregation format
            List<ArticleContentForAggregation> articleContents = articles.stream()
                    .map(this::convertToAggregationFormat)
                    .collect(Collectors.toList());

            // Create input JSON
            String inputJson = objectMapper.writeValueAsString(articleContents);
            int tokenCount = estimateTokenCount(inputJson);

            log.debug("📝 Aggregation input created for {} articles, estimated tokens: {}", articles.size(), tokenCount);

            // Create prompt and call AI
            PromptTemplate promptTemplate = new PromptTemplate(AGGREGATION_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("inputJson", inputJson));

            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getText().trim();

            log.debug("🤖 AI Aggregation Response received: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            // Parse AI response
            AggregationResult result = parseAggregationResponse(aiResponse);

            // Log successful API usage
            logApiUsage(apiKeyName, event.getId(), tokenCount, true, null);

            log.info("✅ Successfully processed aggregation for event '{}'", event.getTitle());
            return result;

        } catch (Exception e) {
            log.error("❌ Error in aggregation for event {}: {}", event.getId(), e.getMessage());
            logApiUsage(apiKeyName, event.getId(), estimateTokenCount("single_event"), false, e.getMessage());
            throw new RuntimeException("এগ্রিগেশন ব্যর্থ: " + e.getMessage(), e);
        }
    }

    /**
     * Get aggregation statistics
     */
    public Map<String, Object> getAggregationStats() {
        Long totalEvents = eventRepository.count();
        Long processedEvents = eventRepository.countByIsProcessed(true);
        Long unprocessedEvents = eventRepository.countByIsProcessed(false);

        Double averageConfidence = eventRepository.getAverageConfidenceScore();
        Double averageArticleCount = eventRepository.getAverageArticleCountPerEvent();

        // Recent activity
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Long recentEvents = eventRepository.countEventsSince(since24h);

        return Map.of(
                "totalEvents", totalEvents != null ? totalEvents : 0,
                "processedEvents", processedEvents != null ? processedEvents : 0,
                "unprocessedEvents", unprocessedEvents != null ? unprocessedEvents : 0,
                "averageConfidenceScore", averageConfidence != null ? Math.round(averageConfidence * 100.0) / 100.0 : 0.0,
                "averageArticleCount", averageArticleCount != null ? Math.round(averageArticleCount * 100.0) / 100.0 : 0.0,
                "recentEventsLast24h", recentEvents != null ? recentEvents : 0,
                "processingPercentage", totalEvents != null && totalEvents > 0 ? Math.round((double) processedEvents / totalEvents * 100) : 0
        );
    }

    // Private helper methods

    private String getNextApiKey() {
        return monitoringService.getNextAvailableApiKey("AGGREGATION");
    }

    private ArticleContentForAggregation convertToAggregationFormat(Article article) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return new ArticleContentForAggregation(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getSource().name(),
                article.getArticlePublishedAt() != null ? article.getArticlePublishedAt().format(formatter) : "",
                article.getUrl()
        );
    }

    private AggregationResult parseAggregationResponse(String aiResponse) throws JsonProcessingException {
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
            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);

            return new AggregationResult(
                    jsonNode.get("aggregatedSummary").asText(),
                    jsonNode.get("discrepancies").asText(),
                    jsonNode.get("confidenceScore").asDouble(),
                    jsonNode.get("methodology").asText()
            );

        } catch (Exception e) {
            log.error("Failed to parse AI aggregation response: {}", cleanedResponse);
            throw new RuntimeException("AI aggregation response parsing failed: " + e.getMessage());
        }
    }

    private void updateEventWithAggregation(Event event, AggregationResult result) {
        event.setAggregatedSummary(result.getAggregatedSummary());
        event.setDiscrepancies(result.getDiscrepancies());
        event.setConfidenceScore(result.getConfidenceScore());
        event.setIsProcessed(true);
        event.setUpdatedAt(LocalDateTime.now());

        eventRepository.save(event);
        log.debug("💾 Updated event {} with aggregation results", event.getId());
    }

    private void logApiUsage(String apiKeyName, Long eventId, Integer tokenCount, Boolean success, String errorMessage) {
        try {
            ApiUsageLog logEntry = ApiUsageLog.builder()
                    .apiKeyName(apiKeyName)
                    .operation("AGGREGATION")
                    .articleId(eventId) // Using this field to store event ID for aggregation operations
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