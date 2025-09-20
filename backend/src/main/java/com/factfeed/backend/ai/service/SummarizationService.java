package com.factfeed.backend.ai.service;

import com.factfeed.backend.ai.dto.SummarizationRequest;
import com.factfeed.backend.ai.dto.SummarizationResponse;
import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.db.repository.ArticleRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
public class SummarizationService {

    // Bangla-specific prompt template for batch processing
    private static final String BANGLA_BATCH_SUMMARIZATION_PROMPT = """
            আপনি একটি বাংলা সংবাদ সারসংক্ষেপ বিশেষজ্ঞ। নিচের JSON format এ দেওয়া সংবাদ নিবন্ধগুলো পড়ুন এবং প্রতিটির জন্য একটি বিস্তৃত, তথ্যবহুল সারাংশ তৈরি করুন।
            
            **গুরুত্বপূর্ণ নির্দেশনা:**
            1. অবশ্যই বাংলায় সারাংশ লিখুন
            2. মূল সংবাদের সকল গুরুত্বপূর্ণ বিষয় এবং প্রধান তথ্যগুলো রাখুন
            3. ৫-৮ বাক্যের মধ্যে বিস্তৃত সারাংশ লিখুন
            4. তারিখ, সংখ্যা, নাম এবং গুরুত্বপূর্ণ বিবরণ অবশ্যই রাখুন
            5. **কোনোভাবেই কোনো article এর id এবং title পরিবর্তন করবেন না**
            6. প্রতিটি নিবন্ধের জন্য আলাদা সারাংশ তৈরি করুন
            
            Input Articles: {inputJson}
            
            অবশ্যই এই exact JSON format এ উত্তর দিন:
            [
              {{
                "id": [original_id_unchanged],
                "title": "[original_title_unchanged]",
                "summarizedContent": "[বাংলায় ৫-৮ বাক্যের বিস্তৃত সারাংশ]"
              }},
              {{
                "id": [original_id_unchanged],
                "title": "[original_title_unchanged]",
                "summarizedContent": "[বাংলায় ৫-৮ বাক্যের বিস্তৃত সারাংশ]"
              }}
            ]
            """;
    private final ChatModel chatModel;
    private final ArticleRepository articleRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;
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
     * Summarize multiple articles in a single AI request (more efficient)
     */
    @Transactional
    public List<SummarizationResponse> summarizeArticlesBatch(List<SummarizationRequest> requests) {
        String apiKeyName = getNextApiKey();
        log.info("🔄 Starting batch summarization for {} articles using key: {}", requests.size(), apiKeyName);

        if (requests.isEmpty()) {
            return List.of();
        }

        try {
            // Create input JSON array
            String inputJson = objectMapper.writeValueAsString(requests);
            int tokenCount = estimateTokenCount(inputJson);

            log.debug("📝 Batch input JSON created, estimated tokens: {}", tokenCount);

            // Create prompt
            PromptTemplate promptTemplate = new PromptTemplate(BANGLA_BATCH_SUMMARIZATION_PROMPT);
            Prompt prompt = promptTemplate.create(Map.of("inputJson", inputJson));

            // Call AI model
            ChatResponse response = chatModel.call(prompt);
            String aiResponse = response.getResult().getOutput().getText().trim();

            log.debug("🤖 AI Batch Response received: {}", aiResponse.substring(0, Math.min(200, aiResponse.length())));

            // Parse batch response
            List<SummarizationResponse> results = parseBatchAiResponse(aiResponse, requests);

            // Validate responses and filter out problematic ones
            List<SummarizationResponse> validResults = validateBatchResponse(requests, results);

            // Log successful usage only for valid articles
            for (SummarizationResponse validResult : validResults) {
                logApiUsage(apiKeyName, validResult.getId(), tokenCount / requests.size(), true, null);
            }

            // Log failed articles (those that were filtered out)
            for (SummarizationRequest request : requests) {
                boolean found = validResults.stream().anyMatch(r -> r.getId().equals(request.getId()));
                if (!found) {
                    logApiUsage(apiKeyName, request.getId(), tokenCount / requests.size(), false, "Title changed or invalid summary");
                }
            }

            // Update database only for valid articles
            updateArticlesSummaries(validResults);

            log.info("✅ Successfully summarized {}/{} articles in batch", validResults.size(), requests.size());
            return validResults;

        } catch (Exception e) {
            log.error("❌ Error in batch summarization: {}", e.getMessage());

            // Log failed usage for all articles
            for (SummarizationRequest request : requests) {
                logApiUsage(apiKeyName, request.getId(), estimateTokenCount(request.toString()), false, e.getMessage());
            }

            throw new RuntimeException("ব্যাচ সারাংশ তৈরি করতে ব্যর্থ: " + e.getMessage(), e);
        }
    }

    /**
     * Summarize a single article with proper JSON handling and API usage logging
     */
    @Transactional
    public SummarizationResponse summarizeArticle(SummarizationRequest request) {
        // For single article, use batch method with one item for consistency
        List<SummarizationResponse> results = summarizeArticlesBatch(List.of(request));
        return results.get(0);
    }

    /**
     * Summarize all unsummarized articles using efficient batch processing
     */
    public String summarizeAllUnsummarized() {
        List<Article> unsummarizedArticles = articleRepository.findUnsummarizedArticles();
        log.info("📊 Found {} articles needing summarization", unsummarizedArticles.size());

        if (unsummarizedArticles.isEmpty()) {
            return "কোনো নিবন্ধের সারাংশ প্রয়োজন নেই";
        }

        int successCount = 0;
        int errorCount = 0;
        int batchSize = 5; // Process 5 articles per batch to optimize API usage

        // Process in batches
        for (int i = 0; i < unsummarizedArticles.size(); i += batchSize) {
            try {
                int endIndex = Math.min(i + batchSize, unsummarizedArticles.size());
                List<Article> batch = unsummarizedArticles.subList(i, endIndex);

                // Convert to request objects
                List<SummarizationRequest> requests = batch.stream()
                        .map(article -> new SummarizationRequest(
                                article.getId(),
                                article.getTitle(),
                                article.getContent()
                        ))
                        .toList();

                // Process batch
                List<SummarizationResponse> results = summarizeArticlesBatch(requests);
                successCount += results.size();

                log.info("📈 Processed batch {}/{}: {} articles",
                        (i / batchSize) + 1,
                        (unsummarizedArticles.size() + batchSize - 1) / batchSize,
                        results.size());

                // Small delay between batches to respect rate limits
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("Error processing batch starting at index {}: {}", i, e.getMessage());
                errorCount += Math.min(batchSize, unsummarizedArticles.size() - i);
            }
        }

        String result = String.format("সারাংশ সম্পন্ন - সফল: %d, ব্যর্থ: %d (ব্যাচ সাইজ: %d)",
                successCount, errorCount, batchSize);
        log.info("📈 Batch summarization completed: {}", result);
        return result;
    }

    /**
     * Get summarization statistics
     */
    public Map<String, Object> getStats() {
        long totalArticles = articleRepository.count();
        long summarizedCount = articleRepository.countSummarizedArticles();
        long unsummarizedCount = totalArticles - summarizedCount;

        // API usage stats for last 24 hours
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        Long apiCallsLast24h = apiUsageLogRepository.countUsageByKeyNameSince("ALL", since24h);

        return Map.of(
                "totalArticles", totalArticles,
                "summarizedArticles", summarizedCount,
                "unsummarizedArticles", unsummarizedCount,
                "summarizedPercentage", totalArticles > 0 ? Math.round((double) summarizedCount / totalArticles * 100) : 0,
                "apiCallsLast24h", apiCallsLast24h != null ? apiCallsLast24h : 0
        );
    }

    // Private helper methods

    private String getNextApiKey() {
        String[] keys = {
                "GEMINI_KEY_Z1", "GEMINI_KEY_Z2", "GEMINI_KEY_Z3",
                "GEMINI_KEY_Z4", "GEMINI_KEY_C5", "GEMINI_KEY_C1"
        };

        int index = keyIndex.getAndIncrement() % keys.length;
        return keys[index];
    }

    private List<SummarizationResponse> parseBatchAiResponse(String aiResponse, List<SummarizationRequest> originalRequests)
            throws JsonProcessingException {

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
            // Parse as array
            SummarizationResponse[] responseArray = objectMapper.readValue(cleanedResponse, SummarizationResponse[].class);
            List<SummarizationResponse> responses = Arrays.asList(responseArray);

            // Validate that we have responses for all requests
            if (responses.size() != originalRequests.size()) {
                log.warn("⚠️ AI returned {} responses for {} requests, attempting to handle missing responses",
                        responses.size(), originalRequests.size());

                // Create a map of returned responses by ID
                Map<Long, SummarizationResponse> responseMap = responses.stream()
                        .collect(Collectors.toMap(SummarizationResponse::getId, r -> r));

                // Fill in missing responses with error placeholders
                List<SummarizationResponse> completeResponses = new ArrayList<>();
                for (SummarizationRequest request : originalRequests) {
                    SummarizationResponse response = responseMap.get(request.getId());
                    if (response != null) {
                        completeResponses.add(response);
                    } else {
                        log.error("❌ No AI response for article ID: {}, creating error placeholder", request.getId());
                        completeResponses.add(new SummarizationResponse(
                                request.getId(),
                                request.getTitle(),
                                "দুঃখিত, এই নিবন্ধের সারাংশ তৈরি করতে সমস্যা হয়েছে।"
                        ));
                    }
                }
                return completeResponses;
            }

            return responses;

        } catch (Exception e) {
            log.error("Failed to parse AI batch response as JSON array: {}", cleanedResponse);
            log.error("Creating fallback responses for all {} articles", originalRequests.size());

            // Create fallback responses for all articles
            return originalRequests.stream()
                    .map(request -> new SummarizationResponse(
                            request.getId(),
                            request.getTitle(),
                            "দুঃখিত, এই নিবন্ধের সারাংশ তৈরি করতে সমস্যা হয়েছে। AI প্রতিক্রিয়া পার্স করা যায়নি।"
                    ))
                    .collect(Collectors.toList());
        }
    }

    private List<SummarizationResponse> validateBatchResponse(List<SummarizationRequest> requests, List<SummarizationResponse> responses) {
        if (requests.size() != responses.size()) {
            log.warn("⚠️ Response count mismatch: expected {}, got {}", requests.size(), responses.size());
        }

        // Create maps for quick lookup
        Map<Long, SummarizationRequest> requestMap = requests.stream()
                .collect(Collectors.toMap(SummarizationRequest::getId, r -> r));

        List<SummarizationResponse> validResponses = new ArrayList<>();

        for (SummarizationResponse response : responses) {
            try {
                SummarizationRequest originalRequest = requestMap.get(response.getId());
                if (originalRequest == null) {
                    log.warn("⚠️ Response contains unknown article ID: {}, skipping", response.getId());
                    continue;
                }

                // If AI changed the title, skip this article entirely
                if (!originalRequest.getTitle().equals(response.getTitle())) {
                    log.warn("⚠️ AI changed title for article ID {}: '{}' -> '{}', skipping this article",
                            response.getId(), originalRequest.getTitle(), response.getTitle());
                    continue; // Skip this article - don't save it to database
                }

                // Check for empty or invalid summary
                if (response.getSummarizedContent() == null || response.getSummarizedContent().trim().isEmpty()) {
                    log.warn("⚠️ AI returned empty summary for article ID {}, skipping", response.getId());
                    continue;
                }

                // Additional validation for summary quality
                String summary = response.getSummarizedContent().trim();
                if (summary.length() < 10) {
                    log.warn("⚠️ Summary too short for article {} ({}), skipping",
                            response.getId(), summary.length());
                    continue;
                }

                validResponses.add(response);
                log.debug("✅ Article {} validated successfully", response.getId());

            } catch (Exception e) {
                log.warn("⚠️ Error validating article {}: {}, skipping", response.getId(), e.getMessage());
            }
        }

        log.info("✅ Validation complete: {}/{} articles will be saved",
                validResponses.size(), responses.size());

        return validResponses;
    }

    private void updateArticlesSummaries(List<SummarizationResponse> responses) {
        for (SummarizationResponse response : responses) {
            updateArticleSummary(response.getId(), response.getSummarizedContent());
        }
    }

    private SummarizationResponse parseAiResponse(String aiResponse, SummarizationRequest originalRequest)
            throws JsonProcessingException {

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

            return new SummarizationResponse(
                    jsonNode.get("id").asLong(),
                    jsonNode.get("title").asText(),
                    jsonNode.get("summarizedContent").asText()
            );

        } catch (Exception e) {
            log.error("Failed to parse AI response as JSON: {}", cleanedResponse);
            throw new RuntimeException("AI response JSON parsing failed: " + e.getMessage());
        }
    }

    private void validateResponse(SummarizationRequest request, SummarizationResponse response) {
        if (!request.getId().equals(response.getId())) {
            throw new RuntimeException("AI changed article ID: " + request.getId() + " -> " + response.getId());
        }

        if (!request.getTitle().equals(response.getTitle())) {
            throw new RuntimeException("AI changed article title");
        }

        if (response.getSummarizedContent() == null || response.getSummarizedContent().trim().isEmpty()) {
            throw new RuntimeException("AI returned empty summary");
        }
    }

    private void updateArticleSummary(Long articleId, String summary) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));

        article.setSummarizedContent(summary);
        articleRepository.save(article);

        log.debug("💾 Updated article {} with summary", articleId);
    }

    private void logApiUsage(String apiKeyName, Long articleId, Integer tokenCount, Boolean success, String errorMessage) {
        try {
            ApiUsageLog log = ApiUsageLog.builder()
                    .apiKeyName(apiKeyName)
                    .operation("SUMMARIZE")
                    .articleId(articleId)
                    .tokenCount(tokenCount)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

            apiUsageLogRepository.save(log);

        } catch (Exception e) {
            log.error("Failed to log API usage: {}", e.getMessage());
        }
    }

    private int estimateTokenCount(String text) {
        // Rough estimation: 1 token ≈ 4 characters for Bangla text
        return Math.max(1, text.length() / 4);
    }
}