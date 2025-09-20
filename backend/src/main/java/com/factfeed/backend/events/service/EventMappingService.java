package com.factfeed.backend.events.service;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.ai.service.ApiUsageMonitoringService;
import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.db.repository.ArticleRepository;
import com.factfeed.backend.events.dto.ArticleCluster;
import com.factfeed.backend.events.dto.ArticleSummaryForMapping;
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
public class EventMappingService {

    private static final String EVENT_MAPPING_PROMPT = """
            আপনি একজন বাংলা সংবাদ বিশ্লেষক বিশেষজ্ঞ। নিচে দেওয়া সংবাদ নিবন্ধগুলো বিশ্লেষণ করে একই ঘটনা বর্ণনাকারী নিবন্ধগুলোকে গ্রুপ করুন।
            
            **নির্দেশনা:**
            1. একই ঘটনা বর্ণনাকারী নিবন্ধগুলো একসাথে গ্রুপ করুন
            2. প্রতিটি গ্রুপের জন্য একটি সংক্ষিপ্ত কিন্তু বর্ণনামূলক শিরোনাম দিন
            3. ঘটনার ধরন নির্ধারণ করুন (রাজনীতি, খেলা, অর্থনীতি, সমাজ, আন্তর্জাতিক, ইত্যাদি)
            4. কেন এই নিবন্ধগুলো একসাথে গ্রুপ করা হয়েছে তার কারণ দিন
            5. প্রতিটি গ্রুপের জন্য ০.০ থেকে ১.০ পর্যন্ত confidence score দিন
            6. কমপক্ষে ২টি নিবন্ধ থাকলেই একটি গ্রুপ তৈরি করুন
            
            **Input Articles:** {inputJson}
            
            অবশ্যই এই exact JSON format এ উত্তর দিন:
            [
              {{
                "eventTitle": "ঘটনার সংক্ষিপ্ত শিরোনাম",
                "eventType": "ঘটনার ধরন",
                "confidenceScore": 0.95,
                "articleIds": [1, 2, 3],
                "reasoning": "কেন এই নিবন্ধগুলো একসাথে গ্রুপ করা হয়েছে"
              }},
              {{
                "eventTitle": "আরেকটি ঘটনার শিরোনাম",
                "eventType": "ঘটনার ধরন",
                "confidenceScore": 0.87,
                "articleIds": [4, 5],
                "reasoning": "গ্রুপিং এর কারণ"
              }}
            ]
            """;

    private final ChatModel chatModel;
    private final ArticleRepository articleRepository;
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
     * Map all unmapped articles to events using AI clustering
     */
    @Transactional
    public String mapAllUnmappedArticles() {
        List<Article> unmappedArticles = mappingRepository.findUnmappedSummarizedArticles();
        log.info("🔍 Found {} unmapped articles for event mapping", unmappedArticles.size());

        if (unmappedArticles.isEmpty()) {
            return "কোনো নতুন নিবন্ধ ম্যাপিং এর প্রয়োজন নেই";
        }

        int mappedCount = 0;
        int eventCount = 0;
        int batchSize = 15; // Process 15 articles per batch for optimal AI clustering

        // Process in batches
        for (int i = 0; i < unmappedArticles.size(); i += batchSize) {
            try {
                int endIndex = Math.min(i + batchSize, unmappedArticles.size());
                List<Article> batch = unmappedArticles.subList(i, endIndex);

                log.info("🔄 Processing event mapping batch {}/{}: {} articles",
                        (i / batchSize) + 1,
                        (unmappedArticles.size() + batchSize - 1) / batchSize,
                        batch.size());

                List<Event> batchEvents = processArticleBatch(batch);
                eventCount += batchEvents.size();
                mappedCount += batch.size();

                // Small delay between batches to respect rate limits
                Thread.sleep(3000);

            } catch (Exception e) {
                log.error("❌ Error processing event mapping batch starting at index {}: {}", i, e.getMessage());
            }
        }

        String result = String.format("ইভেন্ট ম্যাপিং সম্পন্ন - %d টি নিবন্ধ %d টি ইভেন্টে ম্যাপ করা হয়েছে",
                mappedCount, eventCount);
        log.info("📈 Event mapping completed: {}", result);
        return result;
    }

    /**
     * Process a batch of articles and create events with mappings
     */
    @Transactional
    public List<Event> processArticleBatch(List<Article> articles) {
        String apiKeyName = getNextApiKey();
        log.info("🤖 Processing {} articles for event mapping using key: {}", articles.size(), apiKeyName);

        try {
            // Convert articles to mapping format
            List<ArticleSummaryForMapping> summaries = articles.stream()
                    .map(this::convertToSummaryFormat)
                    .collect(Collectors.toList());

            // Create input JSON
            String inputJson = objectMapper.writeValueAsString(summaries);
            int tokenCount = estimateTokenCount(inputJson);

            log.debug("📝 Event mapping input created, estimated tokens: {}", tokenCount);

            // Create prompt and call AI
            PromptTemplate promptTemplate = new PromptTemplate(EVENT_MAPPING_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("inputJson", inputJson));

            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getText().trim();

            log.debug("🤖 AI Event Mapping Response received: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            // Parse AI response into clusters
            List<ArticleCluster> clusters = parseEventMappingResponse(aiResponse);

            // Create events and mappings
            List<Event> createdEvents = createEventsFromClusters(clusters, articles);

            // Log successful API usage
            logApiUsage(apiKeyName, (long) articles.size(), tokenCount, true, null);

            log.info("✅ Successfully created {} events from {} articles", createdEvents.size(), articles.size());
            return createdEvents;

        } catch (Exception e) {
            log.error("❌ Error in event mapping: {}", e.getMessage());
            logApiUsage(apiKeyName, (long) articles.size(), estimateTokenCount("batch"), false, e.getMessage());
            throw new RuntimeException("ইভেন্ট ম্যাপিং ব্যর্থ: " + e.getMessage(), e);
        }
    }

    /**
     * Get event mapping statistics
     */
    public Map<String, Object> getEventMappingStats() {
        Long totalArticles = articleRepository.count();
        Long mappedArticles = mappingRepository.countMappedArticles();
        Long unmappedArticles = totalArticles - mappedArticles;
        Long totalEvents = eventRepository.count();
        Long eventsWithMappings = mappingRepository.countEventsWithMappings();

        Double averageArticlesPerEvent = eventRepository.getAverageArticleCountPerEvent();
        Double averageConfidence = eventRepository.getAverageConfidenceScore();

        // Recent activity
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Long recentEvents = eventRepository.countEventsSince(since24h);

        return Map.of(
                "totalArticles", totalArticles != null ? totalArticles : 0,
                "mappedArticles", mappedArticles != null ? mappedArticles : 0,
                "unmappedArticles", unmappedArticles != null ? unmappedArticles : 0,
                "totalEvents", totalEvents != null ? totalEvents : 0,
                "eventsWithMappings", eventsWithMappings != null ? eventsWithMappings : 0,
                "averageArticlesPerEvent", averageArticlesPerEvent != null ? Math.round(averageArticlesPerEvent * 100.0) / 100.0 : 0.0,
                "averageConfidenceScore", averageConfidence != null ? Math.round(averageConfidence * 100.0) / 100.0 : 0.0,
                "recentEventsLast24h", recentEvents != null ? recentEvents : 0
        );
    }

    // Private helper methods

    private String getNextApiKey() {
        return monitoringService.getNextAvailableApiKey("EVENT_MAPPING");
    }

    private ArticleSummaryForMapping convertToSummaryFormat(Article article) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return new ArticleSummaryForMapping(
                article.getId(),
                article.getTitle(),
                article.getSummarizedContent(),
                article.getArticlePublishedAt() != null ? article.getArticlePublishedAt().format(formatter) : "",
                article.getSource().name()
        );
    }

    private List<ArticleCluster> parseEventMappingResponse(String aiResponse) throws JsonProcessingException {
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
            List<ArticleCluster> clusters = new ArrayList<>();

            for (JsonNode clusterNode : jsonArray) {
                ArticleCluster cluster = new ArticleCluster();
                cluster.setEventTitle(clusterNode.get("eventTitle").asText());
                cluster.setEventType(clusterNode.get("eventType").asText());
                cluster.setConfidenceScore(clusterNode.get("confidenceScore").asDouble());
                cluster.setReasoning(clusterNode.get("reasoning").asText());

                // Parse article IDs
                List<Long> articleIds = new ArrayList<>();
                JsonNode idsArray = clusterNode.get("articleIds");
                for (JsonNode idNode : idsArray) {
                    articleIds.add(idNode.asLong());
                }
                cluster.setArticleIds(articleIds);

                clusters.add(cluster);
            }

            return clusters;

        } catch (Exception e) {
            log.error("Failed to parse AI event mapping response: {}", cleanedResponse);
            throw new RuntimeException("AI event mapping response parsing failed: " + e.getMessage());
        }
    }

    private List<Event> createEventsFromClusters(List<ArticleCluster> clusters, List<Article> articles) {
        List<Event> createdEvents = new ArrayList<>();

        // Create a map for quick article lookup
        Map<Long, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::getId, article -> article));

        for (ArticleCluster cluster : clusters) {
            try {
                // Validate cluster has minimum articles
                if (cluster.getArticleIds().size() < 2) {
                    log.warn("⚠️ Skipping cluster with less than 2 articles: {}", cluster.getEventTitle());
                    continue;
                }

                // Find earliest article date for event date
                LocalDateTime eventDate = cluster.getArticleIds().stream()
                        .map(articleMap::get)
                        .filter(article -> article != null && article.getArticlePublishedAt() != null)
                        .map(Article::getArticlePublishedAt)
                        .min(LocalDateTime::compareTo)
                        .orElse(LocalDateTime.now());

                // Create event
                Event event = Event.builder()
                        .title(cluster.getEventTitle())
                        .eventType(cluster.getEventType())
                        .confidenceScore(cluster.getConfidenceScore())
                        .articleCount(cluster.getArticleIds().size())
                        .eventDate(eventDate)
                        .isProcessed(false) // Will be processed in aggregation step
                        .build();

                Event savedEvent = eventRepository.save(event);

                // Create article mappings
                for (Long articleId : cluster.getArticleIds()) {
                    Article article = articleMap.get(articleId);
                    if (article != null) {
                        ArticleEventMapping mapping = ArticleEventMapping.builder()
                                .article(article)
                                .event(savedEvent)
                                .confidenceScore(cluster.getConfidenceScore())
                                .mappingMethod("AI_CLUSTERING")
                                .build();

                        mappingRepository.save(mapping);
                    } else {
                        log.warn("⚠️ Article with ID {} not found in batch", articleId);
                    }
                }

                createdEvents.add(savedEvent);
                log.info("✅ Created event '{}' with {} articles (confidence: {})",
                        savedEvent.getTitle(), savedEvent.getArticleCount(), savedEvent.getConfidenceScore());

            } catch (Exception e) {
                log.error("❌ Error creating event from cluster: {}", e.getMessage());
            }
        }

        return createdEvents;
    }

    private void logApiUsage(String apiKeyName, Long articleCount, Integer tokenCount, Boolean success, String errorMessage) {
        try {
            ApiUsageLog logEntry = ApiUsageLog.builder()
                    .apiKeyName(apiKeyName)
                    .operation("EVENT_MAPPING")
                    .articleId(articleCount) // Using this field to store article count for batch operations
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