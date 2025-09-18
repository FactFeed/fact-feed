package com.factfeed.backend.ai;

import com.factfeed.backend.article.ArticleService;
import com.factfeed.backend.model.dto.ArticleLightDTO;
import com.factfeed.backend.model.dto.SummarizationResponseDTO;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    private final ChatClient chatClient;
    private final Environment env;
    private final AIService aiService;
    private final ArticleService articleService;

    public AIController(ChatClient.Builder builder, Environment env, AIService aiService, ArticleService articleService) {
        this.chatClient = builder.build();
        this.env = env;
        this.aiService = aiService;
        this.articleService = articleService;
    }

    /**
     * Simple ping endpoint to verify the GenAI pipeline works.
     * Example: GET /api/ai/ask?prompt=Say hello
     */
    @GetMapping("/ask")
    public ResponseEntity<Map<String, Object>> ask(@RequestParam("prompt") String prompt) {
        Map<String, Object> body = new HashMap<>();
        body.put("prompt", prompt);
        try {
            String answer = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            body.put("answer", answer);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
        }
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> body = new HashMap<>();
        String provider = env.getProperty("spring.ai.model.chat");
        String model = env.getProperty("spring.ai.google.genai.chat.options.model",
                env.getProperty("spring.ai.chat.options.model", ""));
        String apiKey = env.getProperty("spring.ai.google.genai.api-key");
        boolean hasApiKey = apiKey != null && !apiKey.isBlank() && !apiKey.contains("${");
        body.put("provider", provider);
        body.put("model", model);
        body.put("hasApiKey", hasApiKey);
        return body;
    }

    /**
     * Summarize a single article
     */
    @PostMapping("/summarize")
    public ResponseEntity<SummarizationResponseDTO> summarizeArticle(@RequestBody ArticleLightDTO article) {
        try {
            SummarizationResponseDTO result = aiService.summarizeArticle(article);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SummarizationResponseDTO.failure(article.getId(), article.getTitle(), e.getMessage()));
        }
    }

    /**
     * Summarize multiple articles
     */
    @PostMapping("/summarize/batch")
    public ResponseEntity<List<SummarizationResponseDTO>> summarizeArticles(@RequestBody List<ArticleLightDTO> articles) {
        try {
            List<SummarizationResponseDTO> results = aiService.summarizeArticles(articles);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Summarize existing unsummarized articles from database
     */
    @PostMapping("/summarize/unsummarized")
    public ResponseEntity<List<SummarizationResponseDTO>> summarizeUnsummarizedArticles(
            @RequestParam(defaultValue = "10") int limit) {
        try {
            List<SummarizationResponseDTO> results = articleService.summarizeUnsummarizedArticles(limit);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get recent articles for clustering analysis
     */
    @GetMapping("/articles/recent")
    public ResponseEntity<List<ArticleLightDTO>> getRecentArticlesForClustering(
            @RequestParam(defaultValue = "10") int hours) {
        try {
            List<ArticleLightDTO> articles = articleService.getRecentArticlesForClustering(hours);
            return ResponseEntity.ok(articles);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
