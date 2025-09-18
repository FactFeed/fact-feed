package com.factfeed.backend.ai;

import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.dto.SummarizationResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;

/**
 * AI service for article summarization and content analysis
 */
@Service
@Slf4j
public class AIService {

    private final ChatClient chatClient;

    public AIService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    private static final String SUMMARIZATION_PROMPT = """
            Please summarize the following news article in 2-3 concise sentences. 
            Focus on the key facts, main events, and important details. 
            Keep the summary neutral and factual.
            Return only the summary text without any additional formatting or labels.
            
            Title: %s
            
            Content: %s""";

    private static final String BATCH_SUMMARIZATION_PROMPT = """
            Please summarize each of the following news articles. For each article, provide a 2-3 sentence summary focusing on key facts and main events. Keep summaries neutral and factual.
            Keep the language consistent with the article title and content. (Almost all the content should be in bangla)
            Return the response as a JSON array with this exact structure:
            [
              {"id": 1, "summary": "Article 1 summary here..."},
              {"id": 2, "summary": "Article 2 summary here..."}
            ]
            
            Articles to summarize:
            %s
            
            JSON Response:""";

    /**
     * Summarize a single article
     */
    public SummarizationResponseDTO summarizeArticle(ArticleLightDTO article) {
        try {
            log.debug("Summarizing article: {}", article.getTitle());
            
            String prompt = String.format(SUMMARIZATION_PROMPT, 
                article.getTitle(), 
                truncateContent(article.getContent())
            );
            
            String summary = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            summary = cleanSummary(summary);
            
            log.info("Successfully summarized article: {}", article.getTitle());
            return SummarizationResponseDTO.success(article.getId(), article.getTitle(), summary);
            
        } catch (Exception e) {
            log.error("Error summarizing article {}: {}", article.getTitle(), e.getMessage());
            return SummarizationResponseDTO.failure(article.getId(), article.getTitle(), e.getMessage());
        }
    }

    /**
     * Summarize multiple articles using batch processing with JSON response
     */
    public List<SummarizationResponseDTO> summarizeArticles(List<ArticleLightDTO> articles) {
        log.info("Starting batch summarization of {} articles", articles.size());
        
        if (articles.isEmpty()) {
            return new ArrayList<>();
        }
        
        // For small batches, use JSON batch processing
        if (articles.size() <= 5) {
            return summarizeArticlesBatch(articles);
        }
        
        // For larger sets, process in smaller chunks to avoid token limits
        List<SummarizationResponseDTO> allResults = new ArrayList<>();
        for (int i = 0; i < articles.size(); i += 5) {
            List<ArticleLightDTO> chunk = articles.subList(i, Math.min(i + 5, articles.size()));
            List<SummarizationResponseDTO> chunkResults = summarizeArticlesBatch(chunk);
            allResults.addAll(chunkResults);
            
            // Small delay between chunks to avoid rate limiting
            if (i + 5 < articles.size()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Batch summarization interrupted");
                    break;
                }
            }
        }
        
        log.info("Completed batch summarization. {}/{} successful", 
            allResults.stream().mapToLong(r -> r.isSuccess() ? 1 : 0).sum(), 
            articles.size());
        
        return allResults;
    }

    /**
     * Process a small batch of articles with JSON response parsing
     */
    private List<SummarizationResponseDTO> summarizeArticlesBatch(List<ArticleLightDTO> articles) {
        try {
            StringBuilder articlesText = new StringBuilder();
            for (int i = 0; i < articles.size(); i++) {
                ArticleLightDTO article = articles.get(i);
                articlesText.append(String.format("%d. Title: %s\n   Content: %s\n\n", 
                    article.getId(), 
                    article.getTitle(),
                    truncateContent(article.getContent())
                ));
            }
            
            String prompt = String.format(BATCH_SUMMARIZATION_PROMPT, articlesText.toString());
            
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            return parseBatchSummarizationResponse(response, articles);
            
        } catch (Exception e) {
            log.error("Error in batch summarization: {}", e.getMessage());
            // Fallback to individual processing
            return articles.stream()
                    .map(this::summarizeArticle)
                    .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        }
    }

    /**
     * Parse JSON response from batch summarization
     */
    private List<SummarizationResponseDTO> parseBatchSummarizationResponse(String response, List<ArticleLightDTO> originalArticles) {
        List<SummarizationResponseDTO> results = new ArrayList<>();
        
        try {
            // Clean the response to extract JSON
            String jsonStr = extractJsonFromResponse(response);
            
            // Simple JSON parsing (could use Jackson for more robust parsing)
            String[] summaries = parseSimpleJsonArray(jsonStr);
            
            for (int i = 0; i < originalArticles.size(); i++) {
                ArticleLightDTO article = originalArticles.get(i);
                if (i < summaries.length && summaries[i] != null) {
                    String summary = cleanSummary(summaries[i]);
                    results.add(SummarizationResponseDTO.success(article.getId(), article.getTitle(), summary));
                } else {
                    results.add(SummarizationResponseDTO.failure(article.getId(), article.getTitle(), "No summary in batch response"));
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing batch response: {}", e.getMessage());
            // Fallback: create failure responses for all articles
            for (ArticleLightDTO article : originalArticles) {
                results.add(SummarizationResponseDTO.failure(article.getId(), article.getTitle(), "JSON parsing failed"));
            }
        }
        
        return results;
    }

    /**
     * Extract JSON array from AI response (simple implementation)
     */
    private String extractJsonFromResponse(String response) {
        if (response == null) return "[]";
        
        // Find the first [ and last ]
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        
        return "[]";
    }

    /**
     * Simple JSON array parser for summary responses
     */
    private String[] parseSimpleJsonArray(String jsonStr) {
        List<String> summaries = new ArrayList<>();
        
        // Simple regex-based parsing for summary field
        String pattern = "\"summary\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(jsonStr);
        
        while (m.find()) {
            summaries.add(m.group(1));
        }
        
        return summaries.toArray(new String[0]);
    }

    /**
     * Truncate content to fit within token limits
     */
    private String truncateContent(String content) {
        if (content == null || content.length() <= 3000) {
            return content;
        }
        
        // Try to truncate at sentence boundary
        String truncated = content.substring(0, 3000);
        int lastSentence = Math.max(
            truncated.lastIndexOf('.'),
            Math.max(truncated.lastIndexOf('!'), truncated.lastIndexOf('?'))
        );
        
        if (lastSentence > 3000 / 2) {
            return truncated.substring(0, lastSentence + 1);
        }
        
        return truncated + "...";
    }

    /**
     * Clean and normalize the AI-generated summary
     */
    private String cleanSummary(String summary) {
        if (summary == null) return null;
        
        return summary
                .trim()
                .replaceAll("^Summary:?\\s*", "") // Remove "Summary:" prefix if present
                .replaceAll("\\s+", " ") // Normalize whitespace
                .trim();
    }
}