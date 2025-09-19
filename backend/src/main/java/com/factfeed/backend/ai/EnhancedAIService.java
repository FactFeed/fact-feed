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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            6. Almost all the content should be in bangla
            
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
            6. Almost all the content should be in bangla
            
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
            8. Almost all the content should be in bangla
            
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
    private final ObjectMapper objectMapper;

    public EnhancedAIService(ChatClient.Builder builder, Environment env, ApiUsageLogRepository apiUsageLogRepository) {
        this.chatClient = builder.build();
        this.env = env;
        this.apiUsageLogRepository = apiUsageLogRepository;
        this.objectMapper = new ObjectMapper();
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
            log.info("Parsing event clustering response");
            log.debug("Raw clustering response: {}", response);

            // Extract JSON array from response
            String jsonStr = extractJsonFromResponse(response);
            log.debug("Extracted JSON for clustering: {}", jsonStr);

            if (jsonStr.isEmpty() || jsonStr.equals("{}")) {
                log.warn("Empty JSON extracted from clustering response");
                return clusters;
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            if (jsonNode.isArray()) {
                // Process array of clusters
                for (JsonNode clusterNode : jsonNode) {
                    try {
                        EventClusteringResponseDTO.EventCluster cluster = parseClusterNode(clusterNode);
                        if (cluster != null) {
                            clusters.add(cluster);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing individual cluster: {}", e.getMessage());
                    }
                }
            } else if (jsonNode.isObject()) {
                // Single cluster object
                EventClusteringResponseDTO.EventCluster cluster = parseClusterNode(jsonNode);
                if (cluster != null) {
                    clusters.add(cluster);
                }
            }

            log.info("Successfully parsed {} clusters", clusters.size());

        } catch (JsonProcessingException e) {
            log.error("JSON parsing error in clustering response: {}", e.getMessage());
            log.debug("Failed clustering response: {}", response);
        } catch (Exception e) {
            log.error("Unexpected error parsing clustering response: {}", e.getMessage());
        }

        return clusters;
    }

    /**
     * Parse individual cluster node
     */
    private EventClusteringResponseDTO.EventCluster parseClusterNode(JsonNode clusterNode) {
        try {
            String title = getJsonFieldAsString(clusterNode, "eventTitle");
            String description = getJsonFieldAsString(clusterNode, "eventDescription");
            String category = getJsonFieldAsString(clusterNode, "category");
            double confidence = getJsonFieldAsDouble(clusterNode, "confidenceScore", 0.0);

            // Parse article IDs
            List<Long> articleIds = new ArrayList<>();
            JsonNode articleIdsNode = clusterNode.get("articleIds");
            if (articleIdsNode != null && articleIdsNode.isArray()) {
                for (JsonNode idNode : articleIdsNode) {
                    try {
                        articleIds.add(idNode.asLong());
                    } catch (Exception e) {
                        log.warn("Invalid article ID in cluster: {}", idNode);
                    }
                }
            }

            // Validate required fields
            if (title.isEmpty() || articleIds.isEmpty()) {
                log.warn("Cluster missing required fields - title: {}, articleIds count: {}", 
                        title.isEmpty() ? "empty" : "present", articleIds.size());
                return null;
            }

            EventClusteringResponseDTO.EventCluster cluster = new EventClusteringResponseDTO.EventCluster();
            cluster.setEventTitle(title);
            cluster.setEventDescription(description.isEmpty() ? "Event cluster" : description);
            cluster.setArticleIds(articleIds);
            cluster.setConfidenceScore(Math.max(0.0, Math.min(1.0, confidence)));
            cluster.setCategory(category.isEmpty() ? "general" : category);
            cluster.setMetadata(new HashMap<>());

            return cluster;

        } catch (Exception e) {
            log.error("Error parsing cluster node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse aggregation response from AI
     */
    private AggregatedContentDTO parseAggregationResponse(String response, Long eventId, int articleCount) {
        try {
            log.info("Parsing aggregation response for event {}", eventId);
            log.debug("Raw AI response: {}", response);

            // Extract JSON from response
            String jsonStr = extractJsonFromResponse(response);
            log.debug("Extracted JSON: {}", jsonStr);

            if (jsonStr.isEmpty() || jsonStr.equals("{}")) {
                log.warn("Empty or invalid JSON extracted from AI response");
                return createFallbackAggregatedContent(eventId, articleCount, "Empty JSON response");
            }

            // Parse with Jackson ObjectMapper for robust JSON handling
            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            // Extract fields with proper null checking and validation
            String title = getJsonFieldAsString(jsonNode, "aggregatedTitle");
            String summary = getJsonFieldAsString(jsonNode, "aggregatedSummary");
            String keyPoints = getJsonFieldAsString(jsonNode, "keyPoints");
            String timeline = getJsonFieldAsString(jsonNode, "timeline");
            double confidence = getJsonFieldAsDouble(jsonNode, "confidenceScore", 0.0);

            // Validate required fields
            if (title.isEmpty() && summary.isEmpty()) {
                log.warn("Both title and summary are empty, creating fallback content");
                return createFallbackAggregatedContent(eventId, articleCount, "Missing required content fields");
            }

            // Create and populate DTO
            AggregatedContentDTO dto = new AggregatedContentDTO();
            dto.setEventId(eventId);
            dto.setAggregatedTitle(title.isEmpty() ? "Aggregated News Update" : title);
            dto.setAggregatedSummary(summary.isEmpty() ? "Content aggregation completed" : summary);
            dto.setKeyPoints(keyPoints);
            dto.setTimeline(timeline);
            dto.setConfidenceScore(Math.max(0.0, Math.min(1.0, confidence))); // Clamp between 0-1
            dto.setTotalArticles(articleCount);
            dto.setCreatedAt(LocalDateTime.now());
            dto.setUpdatedAt(LocalDateTime.now());
            dto.setGeneratedBy(env.getProperty("spring.ai.google.genai.model", "unknown"));

            log.info("Successfully parsed aggregation response for event {} with confidence {}", 
                    eventId, dto.getConfidenceScore());

            return dto;

        } catch (JsonProcessingException e) {
            log.error("JSON parsing error for event {}: {}", eventId, e.getMessage());
            log.debug("Failed to parse response: {}", response);
            return createFallbackAggregatedContent(eventId, articleCount, "JSON parsing error: " + e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error parsing aggregation response for event {}: {}", eventId, e.getMessage());
            log.debug("Failed to parse response: {}", response);
            return createFallbackAggregatedContent(eventId, articleCount, "Parsing error: " + e.getMessage());
        }
    }

    /**
     * Safely extract string field from JSON node
     */
    private String getJsonFieldAsString(JsonNode jsonNode, String fieldName) {
        if (jsonNode == null || !jsonNode.has(fieldName)) {
            return "";
        }
        
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return "";
        }
        
        String value = fieldNode.asText();
        return value != null ? value.trim() : "";
    }

    /**
     * Safely extract double field from JSON node
     */
    private double getJsonFieldAsDouble(JsonNode jsonNode, String fieldName, double defaultValue) {
        if (jsonNode == null || !jsonNode.has(fieldName)) {
            return defaultValue;
        }
        
        JsonNode fieldNode = jsonNode.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return defaultValue;
        }
        
        if (fieldNode.isNumber()) {
            return fieldNode.asDouble();
        }
        
        // Try to parse as string
        try {
            String stringValue = fieldNode.asText();
            return Double.parseDouble(stringValue);
        } catch (NumberFormatException e) {
            log.warn("Could not parse '{}' as double for field '{}', using default: {}", 
                    fieldNode.asText(), fieldName, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Create fallback aggregated content when parsing fails
     */
    private AggregatedContentDTO createFallbackAggregatedContent(Long eventId, int articleCount, String reason) {
        log.warn("Creating fallback aggregated content for event {}: {}", eventId, reason);
        
        AggregatedContentDTO dto = new AggregatedContentDTO();
        dto.setEventId(eventId);
        dto.setAggregatedTitle("Aggregation Processing Error");
        dto.setAggregatedSummary("Could not generate aggregated content. Reason: " + reason);
        dto.setKeyPoints("• Content aggregation failed\n• Please try again later");
        dto.setTimeline("");
        dto.setConfidenceScore(0.0);
        dto.setTotalArticles(articleCount);
        dto.setCreatedAt(LocalDateTime.now());
        dto.setUpdatedAt(LocalDateTime.now());
        dto.setGeneratedBy(env.getProperty("spring.ai.google.genai.model", "unknown"));

        return dto;
    }

    /**
     * Parse discrepancy detection response from AI
     */
    private List<DiscrepancyReportDTO> parseDiscrepancyResponse(String response) {
        List<DiscrepancyReportDTO> discrepancies = new ArrayList<>();

        try {
            log.info("Parsing discrepancy detection response");
            log.debug("Raw discrepancy response: {}", response);

            String jsonStr = extractJsonFromResponse(response);
            log.debug("Extracted JSON for discrepancies: {}", jsonStr);

            if (jsonStr.isEmpty() || jsonStr.equals("{}")) {
                log.warn("Empty JSON extracted from discrepancy response");
                return discrepancies;
            }

            JsonNode jsonNode = objectMapper.readTree(jsonStr);

            if (jsonNode.isArray()) {
                // Process array of discrepancies
                for (JsonNode discrepancyNode : jsonNode) {
                    try {
                        DiscrepancyReportDTO discrepancy = parseDiscrepancyNode(discrepancyNode);
                        if (discrepancy != null) {
                            discrepancies.add(discrepancy);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing individual discrepancy: {}", e.getMessage());
                    }
                }
            } else if (jsonNode.isObject()) {
                // Single discrepancy object
                DiscrepancyReportDTO discrepancy = parseDiscrepancyNode(jsonNode);
                if (discrepancy != null) {
                    discrepancies.add(discrepancy);
                }
            }

            log.info("Successfully parsed {} discrepancies", discrepancies.size());

        } catch (JsonProcessingException e) {
            log.error("JSON parsing error in discrepancy response: {}", e.getMessage());
            log.debug("Failed discrepancy response: {}", response);
        } catch (Exception e) {
            log.error("Unexpected error parsing discrepancy response: {}", e.getMessage());
        }

        return discrepancies;
    }

    /**
     * Parse individual discrepancy node
     */
    private DiscrepancyReportDTO parseDiscrepancyNode(JsonNode discrepancyNode) {
        try {
            String typeStr = getJsonFieldAsString(discrepancyNode, "type");
            String title = getJsonFieldAsString(discrepancyNode, "title");
            String description = getJsonFieldAsString(discrepancyNode, "description");
            String conflictingClaims = getJsonFieldAsString(discrepancyNode, "conflictingClaims");
            String sourcesInvolved = getJsonFieldAsString(discrepancyNode, "sourcesInvolved");
            double severityScore = getJsonFieldAsDouble(discrepancyNode, "severityScore", 0.5);
            double confidenceScore = getJsonFieldAsDouble(discrepancyNode, "confidenceScore", 0.5);

            // Validate and parse discrepancy type
            DiscrepancyReport.DiscrepancyType type;
            try {
                type = DiscrepancyReport.DiscrepancyType.valueOf(typeStr.toUpperCase());
            } catch (Exception e) {
                log.warn("Unknown discrepancy type '{}', using OTHER", typeStr);
                type = DiscrepancyReport.DiscrepancyType.OTHER;
            }

            // Validate required fields
            if (title.isEmpty() || description.isEmpty()) {
                log.warn("Discrepancy missing required fields - title: {}, description: {}", 
                        title.isEmpty() ? "empty" : "present", description.isEmpty() ? "empty" : "present");
                return null;
            }

            DiscrepancyReportDTO dto = new DiscrepancyReportDTO();
            dto.setType(type);
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setConflictingClaims(conflictingClaims.isEmpty() ? "No specific claims identified" : conflictingClaims);
            dto.setSourcesInvolved(sourcesInvolved.isEmpty() ? "Multiple sources" : sourcesInvolved);
            dto.setSeverityScore(Math.max(0.0, Math.min(1.0, severityScore)));
            dto.setConfidenceScore(Math.max(0.0, Math.min(1.0, confidenceScore)));
            dto.setDetectedAt(LocalDateTime.now());
            dto.setDetectedBy(env.getProperty("spring.ai.google.genai.model", "unknown"));

            return dto;

        } catch (Exception e) {
            log.error("Error parsing discrepancy node: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract JSON from AI response with improved detection
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            log.warn("Empty or null response received");
            return "{}";
        }

        String cleanedResponse = response.trim();
        
        // Try to find JSON object boundaries
        int objectStart = cleanedResponse.indexOf('{');
        int objectEnd = cleanedResponse.lastIndexOf('}');
        
        // Try to find JSON array boundaries  
        int arrayStart = cleanedResponse.indexOf('[');
        int arrayEnd = cleanedResponse.lastIndexOf(']');

        String extractedJson = null;

        // Prefer object over array for aggregation responses
        if (objectStart >= 0 && objectEnd > objectStart) {
            extractedJson = cleanedResponse.substring(objectStart, objectEnd + 1);
        } else if (arrayStart >= 0 && arrayEnd > arrayStart) {
            extractedJson = cleanedResponse.substring(arrayStart, arrayEnd + 1);
        }

        if (extractedJson != null) {
            // Validate that we have a reasonable JSON structure
            try {
                objectMapper.readTree(extractedJson);
                log.debug("Successfully extracted and validated JSON");
                return extractedJson;
            } catch (JsonProcessingException e) {
                log.warn("Extracted JSON is invalid: {}", e.getMessage());
                log.debug("Invalid JSON: {}", extractedJson);
            }
        }

        // If no clear JSON boundaries or invalid JSON, try alternative extraction
        log.warn("Could not extract valid JSON from response, attempting alternative parsing");
        
        // Look for JSON-like patterns in the text
        Pattern jsonPattern = Pattern.compile("\\{[^{}]*\"[^\"]*\"\\s*:\\s*\"[^\"]*\"[^{}]*\\}", Pattern.DOTALL);
        Matcher matcher = jsonPattern.matcher(cleanedResponse);
        
        if (matcher.find()) {
            String foundJson = matcher.group();
            try {
                objectMapper.readTree(foundJson);
                log.debug("Found valid JSON pattern in response");
                return foundJson;
            } catch (JsonProcessingException e) {
                log.debug("Found JSON pattern but it's invalid: {}", e.getMessage());
            }
        }

        // Last resort: return the entire response if it looks like JSON
        if (cleanedResponse.startsWith("{") || cleanedResponse.startsWith("[")) {
            log.warn("Returning entire response as potential JSON");
            return cleanedResponse;
        }

        log.error("Could not extract any JSON from response: {}", 
                cleanedResponse.length() > 200 ? cleanedResponse.substring(0, 200) + "..." : cleanedResponse);
        return "{}";
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