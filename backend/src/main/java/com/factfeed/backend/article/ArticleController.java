package com.factfeed.backend.article;

import com.factfeed.backend.model.dto.ArticleDTO;
import com.factfeed.backend.model.dto.ScrapingResultDTO;
import com.factfeed.backend.model.entity.Article;
import com.factfeed.backend.model.enums.NewsSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Slf4j
@Validated
public class ArticleController {

    private final ArticleService articleService;

    // Helper method to create Pageable with validation (DRY principle)
    private Pageable createPageable(int page, int size, String sortBy, String sortDir) {
        // Validate and normalize sort direction
        String normalizedSortDir = sortDir.equalsIgnoreCase("asc") ? "asc" : "desc";

        Sort sort = normalizedSortDir.equals("desc") ?
                Sort.by(sortBy).descending() :
                Sort.by(sortBy).ascending();

        return PageRequest.of(page, size, sort);
    }

    /**
     * Endpoint for Python scraper to submit scraped articles
     */
    @PostMapping("/submit")
    public ResponseEntity<?> submitScrapingResult(@Valid @RequestBody @NotNull ScrapingResultDTO scrapingResult) {
        try {
            // Validate input
            if (scrapingResult.getArticles() == null || scrapingResult.getArticles().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "No articles provided in scraping result"));
            }

            if (scrapingResult.getSiteName() == null || scrapingResult.getSiteName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Site name is required"));
            }

            // Limit batch size for performance
            if (scrapingResult.getArticles().size() > 100) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Maximum 100 articles per batch allowed"));
            }

            log.info("Received scraping result from {} with {} articles",
                    scrapingResult.getSiteName(), scrapingResult.getArticles().size());

            List<Article> savedArticles = articleService.processScrapingResult(scrapingResult);

            return ResponseEntity.ok(Map.of(
                    "message", "Articles processed successfully",
                    "savedCount", savedArticles.size(),
                    "totalReceived", scrapingResult.getArticles().size(),
                    "source", scrapingResult.getSiteName()
            ));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for scraping result: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid input: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing scraping result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process articles. Please try again later."));
        }
    }

    /**
     * Endpoint for Python scraper to submit a single article
     */
    @PostMapping("/submit-single")
    public ResponseEntity<?> submitSingleArticle(@Valid @RequestBody @NotNull ArticleDTO articleDTO) {
        try {
            // Validate input
            if (articleDTO.getTitle() == null || articleDTO.getTitle().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Article title is required"));
            }

            if (articleDTO.getUrl() == null || articleDTO.getUrl().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Article URL is required"));
            }

            // Process as a single-article scraping result
            ScrapingResultDTO singleResult = new ScrapingResultDTO();
            singleResult.setArticles(List.of(articleDTO));
            singleResult.setTotalRequested(1);
            singleResult.setTotalFound(1);
            singleResult.setTotalValid(1);

            List<Article> savedArticles = articleService.processScrapingResult(singleResult);

            if (!savedArticles.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "message", "Article saved successfully",
                        "articleId", savedArticles.get(0).getId(),
                        "url", savedArticles.get(0).getUrl()
                ));
            } else {
                return ResponseEntity.ok(Map.of(
                        "message", "Article was duplicate or invalid",
                        "url", articleDTO.getUrl()
                ));
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for single article: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid input: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing single article", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process article. Please try again later."));
        }
    }

    /**
     * Get all articles with pagination
     */
    @GetMapping
    public ResponseEntity<Page<Article>> getArticles(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = createPageable(page, size, sortBy, sortDir);
        Page<Article> articles = articleService.getAllArticles(pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * Get recent articles within specified hours
     */
    @GetMapping("/recent")
    public ResponseEntity<Page<Article>> getRecentArticles(
            @RequestParam(defaultValue = "24") @Min(1) @Max(168) int hours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = createPageable(page, size, sortBy, sortDir);
        Page<Article> articles = articleService.getRecentArticles(hours, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * Get articles by category
     */
    @GetMapping("/category/{category}")
    public ResponseEntity<Page<Article>> getArticlesByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = createPageable(page, size, sortBy, sortDir);
        Page<Article> articles = articleService.getArticlesByCategory(category, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * Get articles by source
     */
    @GetMapping("/source/{source}")
    public ResponseEntity<Page<Article>> getArticlesBySource(
            @PathVariable NewsSource source,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = createPageable(page, size, sortBy, sortDir);
        Page<Article> articles = articleService.getArticlesBySource(source, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * Search articles by keyword
     */
    @GetMapping("/search")
    public ResponseEntity<Page<Article>> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "publishedDate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Pageable pageable = createPageable(page, size, sortBy, sortDir);
        Page<Article> articles = articleService.searchArticles(keyword, pageable);
        return ResponseEntity.ok(articles);
    }

    /**
     * Health check endpoint for scraper connectivity
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "service", "FactFeed Article API",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}