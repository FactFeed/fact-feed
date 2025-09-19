package com.factfeed.backend.ai;

import com.factfeed.backend.model.dto.AggregatedContentDTO;
import com.factfeed.backend.model.dto.AggregationRequestDTO;
import com.factfeed.backend.model.dto.AggregationResponseDTO;
import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.dto.DiscrepancyReportDTO;
import com.factfeed.backend.model.dto.EventClusteringRequestDTO;
import com.factfeed.backend.model.dto.EventClusteringResponseDTO;
import com.factfeed.backend.model.entity.ApiUsageLog;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.entity.DiscrepancyReport;
import com.factfeed.backend.model.repository.ApiUsageLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Enhanced AI service for event clustering, aggregation, and discrepancy detection
 */
@Service
@Slf4j
public class EnhancedAIService {

    private static final String EVENT_CLUSTERING_PROMPT = """
            Analyze the following news articles and group them into events based on similarity of content and topic.
            Articles about the same event should be grouped together, even if they have slightly different perspectives.
            
            For each event cluster, provide:
            1. A descriptive event title (2-8 words)
            2. A brief event description (1-2 sentences)
            3. The list of article IDs that belong to this event
            4. A confidence score (0.0 to 1.0) for the clustering
            5. The main category/topic
            
            Return the response as a JSON array with this exact structure:
            [
              {
                "eventTitle": "Event title here",
                "eventDescription": "Brief description of what happened",
                "articleIds": [1, 3, 7],
                "confidenceScore": 0.85,
                "category": "politics",
                "metadata": {"keyPeople": ["Person1"], "location": "Location"}
              }
            ]
            
            Articles to cluster:
            %s
            
            JSON Response:""";
    private static final String AGGREGATION_PROMPT = """
            Analyze the following articles that describe the same news event and create an aggregated summary.
            
            Provide:
            1. An aggregated title that captures the main event
            2. A comprehensive summary combining information from all sources
            3. Key points as a bulleted list
            4. Timeline of events if applicable
            5. Confidence score for the aggregation (0.0 to 1.0)
            
            Return the response as JSON:
            {
              "aggregatedTitle": "Combined title here",
              "aggregatedSummary": "Comprehensive summary here",
              "keyPoints": "• Point 1\\n• Point 2\\n• Point 3",
              "timeline": "Timeline if applicable",
              "confidenceScore": 0.90
            }
            
            Articles:
            %s
            
            JSON Response:""";
    private static final String DISCREPANCY_DETECTION_PROMPT = """
            Analyze the following articles about the same event and identify any discrepancies, contradictions, or conflicting information.
            
            For each discrepancy found, provide:
            1. Type of discrepancy (FACTUAL_CONTRADICTION, NUMERICAL_DIFFERENCE, TIMELINE_INCONSISTENCY, etc.)
            2. Title describing the discrepancy
            3. Detailed description of the conflict
            4. Conflicting claims from different sources
            5. Sources involved in the discrepancy
            6. Severity score (0.0 to 1.0)
            7. Confidence score (0.0 to 1.0)
            
            Return as JSON array:
            [
              {
                "type": "FACTUAL_CONTRADICTION",
                "title": "Conflicting casualty numbers",
                "description": "Sources report different numbers of casualties",
                "conflictingClaims": "Source A: 5 injured, Source B: 3 injured",
                "sourcesInvolved": "Prothom Alo, Daily Ittefaq",
                "severityScore": 0.7,
                "confidenceScore": 0.85
              }
            ]
            
            Articles:
            %s
            
            JSON Response:""";
    private final ChatClient chatClient;
    private final Environment env;
    private final ApiUsageLogRepository apiUsageLogRepository;

    public EnhancedAIService(ChatClient.Builder builder, Environment env, ApiUsageLogRepository apiUsageLogRepository) {
        this.chatClient = builder.build();
        this.env = env;
        this.apiUsageLogRepository = apiUsageLogRepository;
    }

    /**
     * Cluster articles into events using AI
     */
    public EventClusteringResponseDTO clusterArticlesIntoEvents(EventClusteringRequestDTO request) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Starting event clustering for {} articles", request.getArticles().size());

            if (request.getArticles().isEmpty()) {
                return EventClusteringResponseDTO.success(new ArrayList<>(), 0, 0.0);
            }

            // Prepare articles text for AI processing
            StringBuilder articlesText = new StringBuilder();
            for (ArticleLightDTO article : request.getArticles()) {
                articlesText.append(String.format("ID: %d\nTitle: %s\nContent: %s\n\n",
                        article.getId(),
                        article.getTitle(),
                        truncateContent(article.getContent(), 1000)
                ));
            }

            String prompt = String.format(EVENT_CLUSTERING_PROMPT, articlesText.toString());

            // Track API usage
            long requestStart = System.currentTimeMillis();
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            long responseTime = System.currentTimeMillis() - requestStart;

            // Log API usage
            logApiUsage("event_clustering", prompt, response, responseTime, true, null);

            // Parse clustering response
            List<EventClusteringResponseDTO.EventCluster> clusters = parseClusteringResponse(response);

            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;

            log.info("Event clustering completed: {} clusters from {} articles in {:.2f}s",
                    clusters.size(), request.getArticles().size(), processingTime);

            return EventClusteringResponseDTO.success(clusters, request.getArticles().size(), processingTime);

        } catch (Exception e) {
            log.error("Error in event clustering: {}", e.getMessage());
            logApiUsage("event_clustering", "", "", 0L, false, e.getMessage());
            return EventClusteringResponseDTO.failure("Event clustering failed: " + e.getMessage());
        }
    }

    /**
     * Generate aggregated content for an event
     */
    public AggregationResponseDTO generateAggregatedContent(AggregationRequestDTO request, List<Article> articles) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Generating aggregated content for event {} with {} articles",
                    request.getEventId(), articles.size());

            if (articles.isEmpty()) {
                return AggregationResponseDTO.failure(request.getEventId(), "No articles provided for aggregation");
            }

            // Prepare articles for aggregation
            StringBuilder articlesText = new StringBuilder();
            for (Article article : articles) {
                articlesText.append(String.format("Source: %s\nTitle: %s\nContent: %s\n\n",
                        article.getSource().name(),
                        article.getTitle(),
                        truncateContent(article.getContent(), 1500)
                ));
            }

            String prompt = String.format(AGGREGATION_PROMPT, articlesText.toString());

            // Get aggregation from AI
            long requestStart = System.currentTimeMillis();
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            long responseTime = System.currentTimeMillis() - requestStart;

            logApiUsage("aggregation", prompt, response, responseTime, true, null);

            // Parse aggregation response
            AggregatedContentDTO aggregatedContent = parseAggregationResponse(response, request.getEventId(), articles.size());

            // Detect discrepancies if requested
            List<DiscrepancyReportDTO> discrepancies = new ArrayList<>();
            if (request.isDetectDiscrepancies() && articles.size() > 1) {
                discrepancies = detectDiscrepancies(articles);
            }

            double processingTime = (System.currentTimeMillis() - startTime) / 1000.0;
            String modelUsed = env.getProperty("spring.ai.google.genai.model", "unknown");

            log.info("Aggregated content generated for event {} in {:.2f}s with {} discrepancies",
                    request.getEventId(), processingTime, discrepancies.size());

            return AggregationResponseDTO.success(request.getEventId(), aggregatedContent,
                    discrepancies, processingTime, modelUsed);

        } catch (Exception e) {
            log.error("Error generating aggregated content for event {}: {}", request.getEventId(), e.getMessage());
            logApiUsage("aggregation", "", "", 0L, false, e.getMessage());
            return AggregationResponseDTO.failure(request.getEventId(), "Aggregation failed: " + e.getMessage());
        }
    }

    /**
     * Detect discrepancies between articles
     */
    public List<DiscrepancyReportDTO> detectDiscrepancies(List<Article> articles) {
        try {
            log.info("Detecting discrepancies among {} articles", articles.size());

            if (articles.size() < 2) {
                return new ArrayList<>();
            }

            // Prepare articles for discrepancy detection
            StringBuilder articlesText = new StringBuilder();
            for (Article article : articles) {
                articlesText.append(String.format("Source: %s\nTitle: %s\nContent: %s\n\n",
                        article.getSource().name(),
                        article.getTitle(),
                        truncateContent(article.getContent(), 1200)
                ));
            }

            String prompt = String.format(DISCREPANCY_DETECTION_PROMPT, articlesText.toString());

            long requestStart = System.currentTimeMillis();
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            long responseTime = System.currentTimeMillis() - requestStart;

            logApiUsage("discrepancy_detection", prompt, response, responseTime, true, null);

            List<DiscrepancyReportDTO> discrepancies = parseDiscrepancyResponse(response);

            log.info("Detected {} discrepancies among {} articles", discrepancies.size(), articles.size());

            return discrepancies;

        } catch (Exception e) {
            log.error("Error detecting discrepancies: {}", e.getMessage());
            logApiUsage("discrepancy_detection", "", "", 0L, false, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Parse event clustering response from AI
     */
    private List<EventClusteringResponseDTO.EventCluster> parseClusteringResponse(String response) {
        List<EventClusteringResponseDTO.EventCluster> clusters = new ArrayList<>();

        try {
            // Extract JSON array from response
            String jsonStr = extractJsonFromResponse(response);

            // Simple JSON parsing for clustering response
            Pattern clusterPattern = Pattern.compile(
                    "\\{[^}]*\"eventTitle\"\\s*:\\s*\"([^\"]+)\"[^}]*\"eventDescription\"\\s*:\\s*\"([^\"]+)\"[^}]*\"articleIds\"\\s*:\\s*\\[([^\\]]+)\\][^}]*\"confidenceScore\"\\s*:\\s*([0-9.]+)[^}]*\"category\"\\s*:\\s*\"([^\"]+)\"[^}]*\\}",
                    Pattern.DOTALL
            );

            Matcher matcher = clusterPattern.matcher(jsonStr);
            while (matcher.find()) {
                String title = matcher.group(1);
                String description = matcher.group(2);
                String articleIdsStr = matcher.group(3);
                double confidence = Double.parseDouble(matcher.group(4));
                String category = matcher.group(5);

                // Parse article IDs
                List<Long> articleIds = new ArrayList<>();
                String[] ids = articleIdsStr.split(",");
                for (String id : ids) {
                    try {
                        articleIds.add(Long.parseLong(id.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid article ID in clustering response: {}", id);
                    }
                }

                if (!articleIds.isEmpty()) {
                    EventClusteringResponseDTO.EventCluster cluster = new EventClusteringResponseDTO.EventCluster();
                    cluster.setEventTitle(title);
                    cluster.setEventDescription(description);
                    cluster.setArticleIds(articleIds);
                    cluster.setConfidenceScore(confidence);
                    cluster.setCategory(category);
                    cluster.setMetadata(new HashMap<>());

                    clusters.add(cluster);
                }
            }

        } catch (Exception e) {
            log.error("Error parsing clustering response: {}", e.getMessage());
        }

        return clusters;
    }

    /**
     * Parse aggregation response from AI
     */
    private AggregatedContentDTO parseAggregationResponse(String response, Long eventId, int articleCount) {
        try {
            // Extract JSON from response
            String jsonStr = extractJsonFromResponse(response);

            // Parse aggregation fields
            String title = extractJsonField(jsonStr, "aggregatedTitle");
            String summary = extractJsonField(jsonStr, "aggregatedSummary");
            String keyPoints = extractJsonField(jsonStr, "keyPoints");
            String timeline = extractJsonField(jsonStr, "timeline");
            double confidence = Double.parseDouble(extractJsonField(jsonStr, "confidenceScore"));

            AggregatedContentDTO dto = new AggregatedContentDTO();
            dto.setEventId(eventId);
            dto.setAggregatedTitle(title);
            dto.setAggregatedSummary(summary);
            dto.setKeyPoints(keyPoints);
            dto.setTimeline(timeline);
            dto.setConfidenceScore(confidence);
            dto.setTotalArticles(articleCount);
            dto.setCreatedAt(LocalDateTime.now());
            dto.setUpdatedAt(LocalDateTime.now());
            dto.setGeneratedBy(env.getProperty("spring.ai.google.genai.model", "unknown"));

            return dto;

        } catch (Exception e) {
            log.error("Error parsing aggregation response: {}", e.getMessage());

            // Return fallback DTO
            AggregatedContentDTO dto = new AggregatedContentDTO();
            dto.setEventId(eventId);
            dto.setAggregatedTitle("Aggregation parsing failed");
            dto.setAggregatedSummary("Could not parse AI response for aggregated content");
            dto.setConfidenceScore(0.0);
            dto.setTotalArticles(articleCount);
            dto.setCreatedAt(LocalDateTime.now());
            dto.setUpdatedAt(LocalDateTime.now());

            return dto;
        }
    }

    /**
     * Parse discrepancy detection response from AI
     */
    private List<DiscrepancyReportDTO> parseDiscrepancyResponse(String response) {
        List<DiscrepancyReportDTO> discrepancies = new ArrayList<>();

        try {
            String jsonStr = extractJsonFromResponse(response);

            // Simple parsing for discrepancy array
            Pattern discrepancyPattern = Pattern.compile(
                    "\\{[^}]*\"type\"\\s*:\\s*\"([^\"]+)\"[^}]*\"title\"\\s*:\\s*\"([^\"]+)\"[^}]*\"description\"\\s*:\\s*\"([^\"]+)\"[^}]*\"conflictingClaims\"\\s*:\\s*\"([^\"]+)\"[^}]*\"sourcesInvolved\"\\s*:\\s*\"([^\"]+)\"[^}]*\"severityScore\"\\s*:\\s*([0-9.]+)[^}]*\"confidenceScore\"\\s*:\\s*([0-9.]+)[^}]*\\}",
                    Pattern.DOTALL
            );

            Matcher matcher = discrepancyPattern.matcher(jsonStr);
            while (matcher.find()) {
                try {
                    DiscrepancyReport.DiscrepancyType type = DiscrepancyReport.DiscrepancyType.valueOf(matcher.group(1));
                    String title = matcher.group(2);
                    String description = matcher.group(3);
                    String conflictingClaims = matcher.group(4);
                    String sourcesInvolved = matcher.group(5);
                    double severityScore = Double.parseDouble(matcher.group(6));
                    double confidenceScore = Double.parseDouble(matcher.group(7));

                    DiscrepancyReportDTO dto = new DiscrepancyReportDTO();
                    dto.setType(type);
                    dto.setTitle(title);
                    dto.setDescription(description);
                    dto.setConflictingClaims(conflictingClaims);
                    dto.setSourcesInvolved(sourcesInvolved);
                    dto.setSeverityScore(severityScore);
                    dto.setConfidenceScore(confidenceScore);
                    dto.setDetectedAt(LocalDateTime.now());
                    dto.setDetectedBy(env.getProperty("spring.ai.google.genai.model", "unknown"));

                    discrepancies.add(dto);

                } catch (Exception e) {
                    log.warn("Error parsing individual discrepancy: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            log.error("Error parsing discrepancy response: {}", e.getMessage());
        }

        return discrepancies;
    }

    /**
     * Extract JSON from AI response
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "{}";

        // Find JSON array or object boundaries
        int arrayStart = response.indexOf('[');
        int arrayEnd = response.lastIndexOf(']');
        int objectStart = response.indexOf('{');
        int objectEnd = response.lastIndexOf('}');

        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return response.substring(arrayStart, arrayEnd + 1);
        } else if (objectStart >= 0 && objectEnd > objectStart) {
            return response.substring(objectStart, objectEnd + 1);
        }

        return response; // Return as-is if no clear JSON boundaries
    }

    /**
     * Extract specific field from JSON string
     */
    private String extractJsonField(String jsonStr, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(jsonStr);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Truncate content to fit within token limits
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }

        String truncated = content.substring(0, maxLength);
        int lastSentence = Math.max(
                truncated.lastIndexOf('.'),
                Math.max(truncated.lastIndexOf('!'), truncated.lastIndexOf('?'))
        );

        if (lastSentence > maxLength / 2) {
            return truncated.substring(0, lastSentence + 1);
        }

        return truncated + "...";
    }

    /**
     * Log API usage for monitoring
     */
    private void logApiUsage(String requestType, String prompt, String response,
                             long responseTimeMs, boolean success, String errorMessage) {
        try {
            ApiUsageLog log = new ApiUsageLog();
            log.setApiProvider("gemini");
            log.setAccountKey(getAccountKeyHash());
            log.setRequestType(requestType);
            log.setModelName(env.getProperty("spring.ai.google.genai.model", "unknown"));
            log.setInputTokens(estimateTokens(prompt));
            log.setOutputTokens(estimateTokens(response));
            log.setTotalTokens(log.getInputTokens() + log.getOutputTokens());
            log.setResponseTimeMs(responseTimeMs);
            log.setSuccess(success);
            log.setErrorMessage(errorMessage);
            log.setCreatedAt(LocalDateTime.now());

            apiUsageLogRepository.save(log);

        } catch (Exception e) {
            log.warn("Failed to log API usage: {}", e.getMessage());
        }
    }

    /**
     * Get a hash of the API key for tracking (privacy-safe)
     */
    private String getAccountKeyHash() {
        String apiKey = env.getProperty("spring.ai.google.genai.api-key", "unknown");
        if (apiKey.length() > 10) {
            return "key_" + apiKey.substring(0, 8).hashCode();
        }
        return "unknown";
    }

    /**
     * Estimate token count (rough approximation)
     */
    private Integer estimateTokens(String text) {
        if (text == null) return 0;
        // Rough estimate: 1 token ≈ 4 characters for English, adjust for Bangla
        return Math.max(1, text.length() / 3);
    }
}