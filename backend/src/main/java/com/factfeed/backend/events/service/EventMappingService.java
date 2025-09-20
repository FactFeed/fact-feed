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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            আপনি একজন বাংলা সংবাদ ক্লাস্টারিং বিশেষজ্ঞ। নিচে দেওয়া সকল সংবাদ নিবন্ধের কীওয়ার্ড-সারসংক্ষেপ বিশ্লেষণ করে একই ঘটনা বর্ণনাকারী নিবন্ধগুলোর ID গুলো গ্রুপ করুন।
            
            **ক্লাস্টারিং নিয়ম:**
            1. **একই ব্যক্তি/সংস্থা + একই ধরনের কাজ/ঘটনা** = একই গ্রুপ
            2. **একই স্থান + একই দিন/সময়ের ঘটনা** = একই গ্রুপ  
            3. **একই বিষয়/ইস্যু নিয়ে আলোচনা** = একই গ্রুপ
            4. **সংখ্যাগত তথ্য (মৃত্যু, আহত, অর্থ) একই** = একই গ্রুপ
            5. প্রতিটি গ্রুপে **কমপক্ষে ২টি** নিবন্ধ থাকতে হবে
            6. একটি নিবন্ধ **শুধুমাত্র একটি গ্রুপে** থাকবে
            7. **সব নিবন্ধকে অবশ্যই কোনো না কোনো গ্রুপে** রাখার চেষ্টা করুন
            8. যদি কোনো নিবন্ধ মিল না হয় তবে সেটাকে সবচেয়ে কাছাকাছি গ্রুপে রাখুন
            
            **গ্রুপ শিরোনাম:** মূল ব্যক্তি/সংস্থা + মূল ঘটনা + স্থান (যদি প্রয়োজন)
            
            **Input Articles:** {inputJson}
            
            অবশ্যই এই exact JSON format এ উত্তর দিন:
            [
              {{
                "eventTitle": "ঘটনার সংক্ষিপ্ত শিরোনাম",
                "eventType": "ঘটনার ধরন",
                "confidenceScore": 0.95,
                "articleIds": [1, 2, 3]
              }},
              {{
                "eventTitle": "আরেকটি ঘটনার শিরোনাম",
                "eventType": "ঘটনার ধরন",
                "confidenceScore": 0.87,
                "articleIds": [4, 5]
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
     * Map all unmapped articles to events using single-batch AI clustering
     */
    @Transactional
    public String mapAllUnmappedArticles() {
        List<Article> unmappedArticles = mappingRepository.findUnmappedSummarizedArticles();
        log.info("🔍 Found {} unmapped articles for event mapping", unmappedArticles.size());

        if (unmappedArticles.isEmpty()) {
            return "কোনো নতুন নিবন্ধ ম্যাপিং এর প্রয়োজন নেই";
        }

        String apiKeyName = getNextApiKey();
        log.info("🤖 Processing all {} articles in single batch using key: {}", unmappedArticles.size(), apiKeyName);

        try {
            // Convert all articles to mapping format
            List<ArticleSummaryForMapping> summaries = unmappedArticles.stream()
                    .map(this::convertToSummaryFormat)
                    .collect(Collectors.toList());

            // Create input JSON for ALL articles
            String inputJson = objectMapper.writeValueAsString(summaries);
            int tokenCount = estimateTokenCount(inputJson);

            log.debug("📝 Single batch input created for {} articles, estimated tokens: {}", unmappedArticles.size(), tokenCount);

            // Create prompt and call AI
            PromptTemplate promptTemplate = new PromptTemplate(EVENT_MAPPING_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("inputJson", inputJson));

            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getText().trim();

            log.debug("🤖 AI Event Mapping Response received: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            // Parse AI response into clusters
            List<ArticleCluster> clusters = parseEventMappingResponse(aiResponse);

            // Create events and mappings from the 2D array
            List<Event> createdEvents = createEventsFromClusters(clusters, unmappedArticles);

            // Log successful API usage
            logApiUsage(apiKeyName, (long) unmappedArticles.size(), tokenCount, true, null);

            String result = String.format("ইভেন্ট ম্যাপিং সম্পন্ন - %d টি নিবন্ধ %d টি ইভেন্টে ম্যাপ করা হয়েছে",
                    unmappedArticles.size(), createdEvents.size());
            log.info("📈 Single-batch event mapping completed: {}", result);
            return result;

        } catch (Exception e) {
            log.error("❌ Error in single-batch event mapping: {}", e.getMessage());
            logApiUsage(apiKeyName, (long) unmappedArticles.size(), estimateTokenCount("batch"), false, e.getMessage());
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
                // Remove reasoning field since it's not in our simplified prompt

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

        // Track which articles have been clustered
        Set<Long> clusteredArticleIds = new HashSet<>();

        for (ArticleCluster cluster : clusters) {
            try {
                // Accept clusters with 1 or more articles (changed from 2+)
                if (cluster.getArticleIds().isEmpty()) {
                    log.warn("⚠️ Skipping empty cluster: {}", cluster.getEventTitle());
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
                        clusteredArticleIds.add(articleId); // Track clustered articles
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

        // Check for unclustered articles and create individual events
        List<Long> unclusteredIds = articles.stream()
                .map(Article::getId)
                .filter(id -> !clusteredArticleIds.contains(id))
                .collect(Collectors.toList());

        if (!unclusteredIds.isEmpty()) {
            log.info("📝 Found {} unclustered articles, creating individual events", unclusteredIds.size());
            
            for (Long articleId : unclusteredIds) {
                Article article = articleMap.get(articleId);
                if (article != null) {
                    // Create individual event for unclustered article
                    Event individualEvent = Event.builder()
                            .title("স্বতন্ত্র সংবাদ: " + article.getTitle().substring(0, Math.min(50, article.getTitle().length())))
                            .eventType("বিবিধ")
                            .confidenceScore(0.5) // Lower confidence for individual events
                            .articleCount(1)
                            .eventDate(article.getArticlePublishedAt() != null ? 
                                      article.getArticlePublishedAt() : LocalDateTime.now())
                            .isProcessed(false)
                            .build();
                    
                    Event savedIndividualEvent = eventRepository.save(individualEvent);
                    
                    // Create mapping
                    ArticleEventMapping individualMapping = ArticleEventMapping.builder()
                            .article(article)
                            .event(savedIndividualEvent)
                            .confidenceScore(0.5)
                            .mappingMethod("AI_INDIVIDUAL")
                            .build();
                    
                    mappingRepository.save(individualMapping);
                    createdEvents.add(savedIndividualEvent);
                    
                    log.debug("📄 Created individual event for article: {}", article.getTitle().substring(0, Math.min(30, article.getTitle().length())));
                }
            }
        }

        log.info("📊 Clustering Summary: {} clustered events, {} individual events, {} total articles processed", 
                createdEvents.size() - unclusteredIds.size(), unclusteredIds.size(), articles.size());

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