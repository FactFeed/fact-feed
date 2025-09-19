package com.factfeed.backend.scraper.extraction;

import com.factfeed.backend.scraper.model.ArticleData;
import com.factfeed.backend.scraper.model.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JsonLdExtractorService implements ArticleExtractor {

    private static final String JSON_LD_SELECTOR = "script[type=\"application/ld+json\"]";
    private static final String[] TITLE_SELECTORS = {
            "h1.entry-title", "h1.post-title", "h1.article-title",
            ".article-title h1", ".post-header h1", "h1"
    };
    private static final String[] CONTENT_SELECTORS = {
            ".entry-content", ".post-content", ".article-content",
            ".content-area", ".article-body", ".post-body"
    };
    private static final String[] AUTHOR_SELECTORS = {
            ".author-name", ".byline", ".author", ".post-author",
            "[rel=\"author\"]", ".article-author"
    };
    private final ObjectMapper objectMapper;

    public JsonLdExtractorService() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ArticleData extractArticle(String url, String siteName) {
        log.info("Extracting article from {} using JSON-LD method", url);

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
                    .get();

            // Try JSON-LD extraction first
            ArticleData articleData = extractFromJsonLd(doc, url, siteName);

            // If JSON-LD doesn't provide complete data, supplement with HTML extraction
            if (articleData == null || !articleData.isValid().isValid()) {
                log.debug("JSON-LD extraction incomplete, falling back to HTML extraction");
                articleData = extractFromHtml(doc, url, siteName);
            }

            return articleData;

        } catch (IOException e) {
            log.error("Failed to fetch article from {}: {}", url, e.getMessage());
            return createEmptyArticle(url, siteName, "Failed to fetch: " + e.getMessage());
        }
    }

    private ArticleData extractFromJsonLd(Document doc, String url, String siteName) {
        try {
            Elements jsonLdElements = doc.select(JSON_LD_SELECTOR);

            for (Element jsonLdElement : jsonLdElements) {
                String jsonContent = jsonLdElement.html();
                try {
                    JsonNode jsonNode = objectMapper.readTree(jsonContent);

                    if (jsonNode.isArray()) {
                        for (JsonNode item : jsonNode) {
                            ArticleData article = parseJsonLdNode(item, url, siteName);
                            if (article != null) {
                                return article;
                            }
                        }
                    } else {
                        ArticleData article = parseJsonLdNode(jsonNode, url, siteName);
                        if (article != null) {
                            return article;
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.debug("Failed to parse JSON-LD: {}", e.getMessage());
                }
            }

            log.debug("No valid JSON-LD data found");
            return null;

        } catch (Exception e) {
            log.debug("Error extracting from JSON-LD: {}", e.getMessage());
            return null;
        }
    }

    private ArticleData parseJsonLdNode(JsonNode node, String url, String siteName) {
        try {
            JsonNode typeNode = node.get("@type");
            if (typeNode == null) {
                return null;
            }

            String type = typeNode.asText().toLowerCase();
            if (!type.contains("article") && !type.contains("newsarticle")) {
                return null;
            }

            ArticleData.ArticleDataBuilder builder = ArticleData.builder();

            // Extract title
            if (node.has("headline")) {
                builder.title(node.get("headline").asText());
            } else if (node.has("name")) {
                builder.title(node.get("name").asText());
            }

            // Extract content
            if (node.has("articleBody")) {
                builder.content(node.get("articleBody").asText());
            } else if (node.has("description")) {
                builder.content(node.get("description").asText());
            }

            // Extract author
            List<String> authors = new ArrayList<>();
            if (node.has("author")) {
                JsonNode authorNode = node.get("author");
                if (authorNode.isArray()) {
                    for (JsonNode author : authorNode) {
                        String authorName = extractAuthorName(author);
                        if (authorName != null) {
                            authors.add(authorName);
                        }
                    }
                } else {
                    String authorName = extractAuthorName(authorNode);
                    if (authorName != null) {
                        authors.add(authorName);
                    }
                }
            }
            builder.authors(authors);

            // Extract dates (convert to String format for ArticleData)
            if (node.has("datePublished")) {
                try {
                    String dateStr = node.get("datePublished").asText();
                    LocalDateTime publishedDate = parseDateTime(dateStr);
                    // Format as string for ArticleData
                    String formattedDate = publishedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    builder.publishedAt(formattedDate);
                } catch (Exception e) {
                    log.debug("Failed to parse published date: {}", e.getMessage());
                }
            }

            if (node.has("dateModified")) {
                try {
                    String dateStr = node.get("dateModified").asText();
                    LocalDateTime modifiedDate = parseDateTime(dateStr);
                    // Format as string for ArticleData
                    String formattedDate = modifiedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    builder.updatedAt(formattedDate);
                } catch (Exception e) {
                    log.debug("Failed to parse modified date: {}", e.getMessage());
                }
            }

            // Extract image
            if (node.has("image")) {
                JsonNode imageNode = node.get("image");
                String imageUrl = null;
                if (imageNode.isTextual()) {
                    imageUrl = imageNode.asText();
                } else if (imageNode.isObject() && imageNode.has("url")) {
                    imageUrl = imageNode.get("url").asText();
                }
                builder.imageUrl(imageUrl);
            }

            // Set metadata
            builder.url(url)
                    .sourceSite(siteName)
                    .extractedAt(LocalDateTime.now())
                    .extractionMethod("JSON-LD");

            return builder.build();

        } catch (Exception e) {
            log.debug("Error parsing JSON-LD node: {}", e.getMessage());
            return null;
        }
    }

    private String extractAuthorName(JsonNode authorNode) {
        if (authorNode.isTextual()) {
            return authorNode.asText();
        } else if (authorNode.isObject()) {
            if (authorNode.has("name")) {
                return authorNode.get("name").asText();
            } else if (authorNode.has("@name")) {
                return authorNode.get("@name").asText();
            }
        }
        return null;
    }

    private ArticleData extractFromHtml(Document doc, String url, String siteName) {
        log.debug("Extracting article from HTML for {}", url);

        ArticleData.ArticleDataBuilder builder = ArticleData.builder();

        String title = extractWithSelectors(doc, TITLE_SELECTORS);
        if (title == null || title.trim().isEmpty()) {
            title = doc.title();
        }
        builder.title(title);

        String content = extractWithSelectors(doc, CONTENT_SELECTORS);
        if (content != null) {
            Document contentDoc = Jsoup.parse(content);
            contentDoc.select("script, style, .advertisement, .ads").remove();
            content = contentDoc.text();
        }
        builder.content(content);

        String authorText = extractWithSelectors(doc, AUTHOR_SELECTORS);
        List<String> authors = new ArrayList<>();
        if (authorText != null && !authorText.trim().isEmpty()) {
            authors.add(authorText.trim());
        }
        builder.authors(authors);

        Element publishedMeta = doc.selectFirst("meta[property=\"article:published_time\"], meta[name=\"publish-date\"]");
        if (publishedMeta != null) {
            try {
                String dateStr = publishedMeta.attr("content");
                LocalDateTime publishedDate = parseDateTime(dateStr);
                // Format as string for ArticleData
                String formattedDate = publishedDate.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                builder.publishedAt(formattedDate);
            } catch (Exception e) {
                log.debug("Failed to parse published date from meta: {}", e.getMessage());
            }
        }

        Element ogImage = doc.selectFirst("meta[property=\"og:image\"]");
        if (ogImage != null) {
            builder.imageUrl(ogImage.attr("content"));
        }

        builder.url(url)
                .sourceSite(siteName)
                .extractedAt(LocalDateTime.now())
                .extractionMethod("HTML");

        return builder.build();
    }

    private String extractWithSelectors(Document doc, String[] selectors) {
        for (String selector : selectors) {
            Element element = doc.selectFirst(selector);
            if (element != null) {
                String text = element.text();
                if (!text.trim().isEmpty()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        // Common date formats
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",  // ISO with timezone
                "yyyy-MM-dd'T'HH:mm:ssXXX",      // ISO without milliseconds
                "yyyy-MM-dd'T'HH:mm:ss",         // ISO simple
                "yyyy-MM-dd HH:mm:ss",           // Standard format
                "yyyy-MM-dd"                     // Date only
        };

        for (String pattern : patterns) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception e) {
                // Try next pattern
            }
        }

        log.debug("Failed to parse date: {}", dateStr);
        return LocalDateTime.now();
    }

    private ArticleData createEmptyArticle(String url, String siteName, String error) {
        return ArticleData.builder()
                .url(url)
                .sourceSite(siteName)
                .extractedAt(LocalDateTime.now())
                .extractionMethod("ERROR")
                .title("Extraction Failed")
                .content("Failed to extract content: " + error)
                .authors(new ArrayList<>())
                .build();
    }

    @Override
    public ValidationResult validateUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return ValidationResult.failure("URL cannot be empty");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ValidationResult.failure("URL must start with http:// or https://");
        }

        return ValidationResult.success();
    }

    @Override
    public String getSiteName() {
        return "json-ld-extractor";
    }

    @Override
    public String getExtractionMethod() {
        return "JSON-LD";
    }
}