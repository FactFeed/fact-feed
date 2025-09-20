package com.factfeed.backend.ai.controller;

import com.factfeed.backend.ai.dto.SummarizationRequest;
import com.factfeed.backend.ai.dto.SummarizationResponse;
import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.ai.service.SummarizationService;
import com.factfeed.backend.db.entity.Article;
import com.factfeed.backend.db.repository.ArticleRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/summarization")
@RequiredArgsConstructor
public class SummarizationController {

    private final SummarizationService summarizationService;
    private final ArticleRepository articleRepository;
    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * Summarize multiple articles in a single batch request (more efficient)
     */
    @PostMapping("/summarize-batch")
    public ResponseEntity<List<SummarizationResponse>> summarizeArticlesBatch(@RequestBody List<SummarizationRequest> requests) {
        log.info("📥 Received batch summarization request for {} articles", requests.size());

        if (requests.size() > 10) {
            return ResponseEntity.badRequest().build(); // Limit batch size
        }

        try {
            List<SummarizationResponse> responses = summarizationService.summarizeArticlesBatch(requests);
            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("❌ Batch summarization failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Summarize a single article by providing JSON input
     */
    @PostMapping("/summarize")
    public ResponseEntity<SummarizationResponse> summarizeArticle(@RequestBody SummarizationRequest request) {
        log.info("📥 Received summarization request for article ID: {}", request.getId());

        try {
            SummarizationResponse response = summarizationService.summarizeArticle(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Summarization failed: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Summarize a specific article by ID (fetch from database)
     */
    @PostMapping("/summarize/{articleId}")
    public ResponseEntity<SummarizationResponse> summarizeArticleById(@PathVariable Long articleId) {
        log.info("📥 Received summarization request for article ID: {}", articleId);

        try {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new RuntimeException("Article not found: " + articleId));

            SummarizationRequest request = new SummarizationRequest(
                    article.getId(),
                    article.getTitle(),
                    article.getContent()
            );

            SummarizationResponse response = summarizationService.summarizeArticle(request);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ Summarization failed for article {}: {}", articleId, e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Summarize all unsummarized articles
     */
    @PostMapping("/summarize-all")
    public ResponseEntity<Map<String, Object>> summarizeAllUnsummarized() {
        log.info("🚀 Starting batch summarization of all unsummarized articles");

        try {
            String result = summarizationService.summarizeAllUnsummarized();

            return ResponseEntity.ok(Map.of(
                    "message", result,
                    "status", "COMPLETED"
            ));

        } catch (Exception e) {
            log.error("❌ Batch summarization failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Batch summarization failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get summarization statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = summarizationService.getStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get unsummarized articles (for manual processing)
     */
    @GetMapping("/unsummarized")
    public ResponseEntity<List<Article>> getUnsummarizedArticles() {
        List<Article> articles = articleRepository.findUnsummarizedArticles();
        return ResponseEntity.ok(articles);
    }

    /**
     * Get API usage logs (for monitoring)
     */
    @GetMapping("/api-usage")
    public ResponseEntity<List<ApiUsageLog>> getApiUsageLogs(@RequestParam(defaultValue = "50") int limit) {
        List<ApiUsageLog> logs = apiUsageLogRepository.findAll().stream()
                .limit(limit)
                .toList();
        return ResponseEntity.ok(logs);
    }

    /**
     * Get failed API operations
     */
    @GetMapping("/api-usage/failures")
    public ResponseEntity<List<ApiUsageLog>> getFailedOperations() {
        List<ApiUsageLog> failures = apiUsageLogRepository.findFailedOperations();
        return ResponseEntity.ok(failures);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "HEALTHY",
                "service", "Summarization Service",
                "timestamp", System.currentTimeMillis()
        ));
    }
}