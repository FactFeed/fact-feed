package com.factfeed.backend.scraping;

import com.factfeed.backend.config.NewsSourceConfig;
import com.factfeed.backend.config.ScrapingConfig;
import com.factfeed.backend.model.dto.ArticleDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

/**
 * Core article extraction service using JSoup and news-sources.yml configuration
 */
@Service
@Slf4j
public class ArticleExtractorService {

    private final ScrapingConfig scrapingConfig;
    private final ObjectMapper objectMapper;
    private final Pattern jsonLdPattern = Pattern.compile("\\{.*?\\}", Pattern.DOTALL);
    private final Set<String> allowedJsonLdTypesLowercase;

    public ArticleExtractorService(ScrapingConfig scrapingConfig) {
        this.scrapingConfig = scrapingConfig;
        this.objectMapper = new ObjectMapper();
        // Pre-compute lowercase set for O(1) lookup
        this.allowedJsonLdTypesLowercase = scrapingConfig.getAllowedJsonLdTypes() != null
                ? scrapingConfig.getAllowedJsonLdTypes().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet())
                : Set.of();
    }

    /**
     * Extract article data from a URL using the provided news source configuration
     */
    public ArticleDTO extractArticle(String url, NewsSourceConfig config) {
        log.info("Extracting article from URL: {}", url);

        if (shouldSkipUrl(url)) {
            log.debug("Skipping excluded URL: {}", url);
            return null;
        }

        try {
            // Fetch the page with simple retry/backoff
            Document doc = null;
            int attempts = Math.max(1, scrapingConfig.getDefaultMaxRetries());
            for (int i = 0; i < attempts; i++) {
                try {
                    doc = Jsoup.connect(url)
                            .userAgent(scrapingConfig.getDefaultHeaders().get("User-Agent"))
                            .timeout(scrapingConfig.getDefaultTimeout() * 1000)
                            .headers(scrapingConfig.getDefaultHeaders())
                            .get();
                    break;
                } catch (IOException ex) {
                    if (i == attempts - 1) throw ex;
                    try {
                        Thread.sleep((long) (scrapingConfig.getDefaultDelay() * 1000L * (i + 1)));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            if (doc == null) return null;

            // Extract article data using selectors
            ArticleDTO article = new ArticleDTO();
            article.setUrl(url);

            // Extract each field using priority-based selectors
            article.setTitle(extractText(doc, config.getTitleSelectors(), "title"));
            article.setAuthor(extractText(doc, config.getAuthorSelectors(), "author"));
            article.setAuthorLocation(extractText(doc, config.getAuthorLocationSelectors(), "authorLocation"));
            article.setPublishedAt(extractText(doc, config.getPublishedTimeSelectors(), "publishedTime"));
            article.setUpdatedAt(extractText(doc, config.getUpdatedTimeSelectors(), "updatedTime"));
            article.setImageUrl(extractImageUrl(doc, config.getImageSelectors()));
            article.setImageCaption(extractText(doc, config.getImageCaptionSelectors(), "imageCaption"));
            article.setContent(extractContent(doc, config.getContentSelectors(), config.getContentIgnoreSelectors()));
            article.setCategory(extractText(doc, config.getCategorySelectors(), "category"));
            article.setTags(extractText(doc, config.getKeywordSelectors(), "keywords"));

            // Log extraction results for debugging
            log.debug("Extracted data for {}: title={}, author={}, image={}, content_length={}",
                    url,
                    article.getTitle() != null ? article.getTitle().substring(0, Math.min(50, article.getTitle().length())) : "null",
                    article.getAuthor(),
                    article.getImageUrl(),
                    article.getContent() != null ? article.getContent().length() : 0);

            // Validate extracted article
            if (isValidArticle(article)) {
                log.info("Successfully extracted article: {}", article.getTitle());
                return article;
            } else {
                log.warn("Invalid article extracted from URL: {} - title={}, content_length={}",
                        url, article.getTitle(), article.getContent() != null ? article.getContent().length() : 0);
                return null;
            }

        } catch (IOException e) {
            log.error("Error fetching URL {}: {}", url, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Error extracting article from URL {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extract text using priority-based selectors, including JSON-LD support
     */
    private String extractText(Document doc, List<String> selectors, String fieldType) {
        if (selectors == null || selectors.isEmpty()) {
            return null;
        }

        for (String selector : selectors) {
            try {
                if (selector.contains("script[type='application/ld+json']")) {
                    String jsonLdResult = extractFromJsonLd(doc, fieldType);
                    if (jsonLdResult != null && !jsonLdResult.trim().isEmpty()) {
                        return jsonLdResult.trim();
                    }
                } else {
                    Elements elements = doc.select(selector);
                    if (!elements.isEmpty()) {
                        Element el = elements.first();
                        // Prefer machine-readable attributes for dates and metas
                        if ("publishedTime".equals(fieldType) || "updatedTime".equals(fieldType)) {
                            String attr = el.hasAttr("datetime") ? el.attr("datetime") :
                                    (el.hasAttr("dateTime") ? el.attr("dateTime") :
                                            (el.hasAttr("content") ? el.attr("content") : null));
                            if (attr != null && !attr.trim().isEmpty()) return attr.trim();
                        }

                        if (el.hasAttr("content")) {
                            String content = el.attr("content");
                            if (content != null && !content.trim().isEmpty()) return content.trim();
                        }

                        String result = el.text();
                        if (result != null && !result.trim().isEmpty()) {
                            return result.trim();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error with selector '{}' for field '{}': {}", selector, fieldType, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Extract content using priority-based selectors and ignore unwanted elements
     */
    private String extractContent(Document doc, List<String> contentSelectors, List<String> ignoreSelectors) {
        if (contentSelectors == null || contentSelectors.isEmpty()) {
            return null;
        }

        for (String selector : contentSelectors) {
            try {
                if (selector.contains("script[type='application/ld+json']")) {
                    String jsonLdResult = extractFromJsonLd(doc, "content");
                    if (jsonLdResult != null && !jsonLdResult.trim().isEmpty()) {
                        return cleanContent(jsonLdResult);
                    }
                } else {
                    Elements elements = doc.select(selector);
                    if (!elements.isEmpty()) {
                        // Remove ignored elements
                        if (ignoreSelectors != null) {
                            for (String ignoreSelector : ignoreSelectors) {
                                elements.select(ignoreSelector).remove();
                            }
                        }

                        StringBuilder content = new StringBuilder();
                        for (Element element : elements) {
                            String text = element.text();
                            if (text != null && !text.trim().isEmpty()) {
                                content.append(text.trim()).append(" ");
                            }
                        }

                        String result = content.toString().trim();
                        if (!result.isEmpty()) {
                            return cleanContent(result);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error with content selector '{}': {}", selector, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Extract image URL using priority-based selectors
     */
    private String extractImageUrl(Document doc, List<String> selectors) {
        if (selectors == null || selectors.isEmpty()) {
            return null;
        }

        for (String selector : selectors) {
            try {
                if (selector.contains("script[type='application/ld+json']")) {
                    String jsonLdResult = extractFromJsonLd(doc, "image");
                    if (jsonLdResult != null && !jsonLdResult.trim().isEmpty()) {
                        return jsonLdResult.trim();
                    }
                } else {
                    Elements elements = doc.select(selector);
                    if (!elements.isEmpty()) {
                        Element element = elements.first();
                        String imageUrl = null;

                        // Try different attributes for image URL
                        if (element.hasAttr("content")) {
                            imageUrl = element.attr("content");
                        } else if (element.hasAttr("src")) {
                            imageUrl = element.attr("src");
                        } else if (element.hasAttr("data-src")) {
                            imageUrl = element.attr("data-src");
                        }

                        if (imageUrl != null && !imageUrl.trim().isEmpty() && !shouldSkipImage(imageUrl)) {
                            return imageUrl.trim();
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Error with image selector '{}': {}", selector, e.getMessage());
            }
        }
        return null;
    }

    /**
     * Extract data from JSON-LD structured data
     */
    private String extractFromJsonLd(Document doc, String fieldType) {
        Elements scripts = doc.select("script[type='application/ld+json']");

        for (Element script : scripts) {
            try {
                String jsonContent = script.html();
                if (jsonContent == null || jsonContent.trim().isEmpty()) continue;
                // Some sites wrap multiple JSON objects without an array; try to parse robustly
                JsonNode jsonNode = objectMapper.readTree(jsonContent);

                // Handle both single objects and arrays
                if (jsonNode.isArray()) {
                    for (JsonNode node : jsonNode) {
                        // Skip breadcrumb blocks
                        JsonNode typeNode = node.get("@type");
                        if (typeNode != null && "BreadcrumbList".equals(typeNode.asText())) continue;
                        String result = extractFieldFromJsonNode(node, fieldType);
                        if (result != null) return result;
                    }
                } else {
                    JsonNode typeNode = jsonNode.get("@type");
                    if (typeNode != null && "BreadcrumbList".equals(typeNode.asText())) continue;
                    String result = extractFieldFromJsonNode(jsonNode, fieldType);
                    if (result != null) return result;
                }
            } catch (Exception e) {
                log.debug("Error parsing JSON-LD: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * Extract specific field from JSON-LD node
     */
    private String extractFieldFromJsonNode(JsonNode node, String fieldType) {
        // Only process allowed JSON-LD @type values (configurable), skip BreadcrumbList etc.
        JsonNode typeNode = node.get("@type");
        if (typeNode == null) {
            return null;
        }

        String nodeType = typeNode.asText();
        if (allowedJsonLdTypesLowercase.isEmpty() ||
                !allowedJsonLdTypesLowercase.contains(nodeType.toLowerCase())) {
            return null;
        }

        switch (fieldType) {
            case "title":
                return getJsonValue(node, "headline", "name");
            case "author":
                return extractAuthor(node);
            case "publishedTime":
                return getJsonValue(node, "datePublished");
            case "updatedTime":
                return getJsonValue(node, "dateModified");
            case "image":
                return extractImageFromJson(node);
            case "content":
                String content = getJsonValue(node, "articleBody");
                return content != null ? unescapeHtmlEntities(content) : null;
            case "category":
                return getJsonValue(node, "articleSection", "section");
            case "keywords":
                return getJsonValue(node, "keywords");
            default:
                return null;
        }
    }

    private String extractAuthor(JsonNode node) {
        JsonNode authorNode = node.get("author");
        if (authorNode != null) {
            if (authorNode.isArray() && !authorNode.isEmpty()) {
                JsonNode firstAuthor = authorNode.get(0);
                return getJsonValue(firstAuthor, "name", "givenName");
            } else if (authorNode.isObject()) {
                return getJsonValue(authorNode, "name", "givenName");
            } else {
                return authorNode.asText();
            }
        }
        return null;
    }

    private String extractImageFromJson(JsonNode node) {
        JsonNode imageNode = node.get("image");
        if (imageNode != null) {
            if (imageNode.isArray() && !imageNode.isEmpty()) {
                JsonNode firstImage = imageNode.get(0);
                return getJsonValue(firstImage, "url", "contentUrl");
            } else if (imageNode.isObject()) {
                return getJsonValue(imageNode, "url", "contentUrl");
            } else {
                return imageNode.asText();
            }
        }
        return null;
    }

    private String getJsonValue(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode fieldNode = node.get(field);
            if (fieldNode != null && !fieldNode.isNull()) {
                return fieldNode.asText();
            }
        }
        return null;
    }

    /**
     * Clean and normalize content text
     */
    private String cleanContent(String content) {
        if (content == null) return null;

        return content
                .replaceAll("\\s+", " ")  // Normalize whitespace
                .replaceAll("&nbsp;", " ")  // Replace non-breaking spaces
                .replaceAll("&amp;", "&")  // Unescape ampersands
                .replaceAll("&lt;", "<")   // Unescape less than
                .replaceAll("&gt;", ">")   // Unescape greater than
                .replaceAll("&quot;", "\"") // Unescape quotes
                .trim();
    }

    /**
     * Check if URL should be skipped based on exclusion patterns
     */
    private boolean shouldSkipUrl(String url) {
        if (url == null) return true;

        String lowerUrl = url.toLowerCase();
        return scrapingConfig.getExcludedUrlPatterns().stream()
                .anyMatch(pattern -> lowerUrl.contains(pattern.toLowerCase()));
    }

    /**
     * Check if image URL should be skipped based on exclusion patterns
     */
    private boolean shouldSkipImage(String imageUrl) {
        if (imageUrl == null) return true;

        String lowerUrl = imageUrl.toLowerCase();
        return scrapingConfig.getExcludedImagePatterns().stream()
                .anyMatch(pattern -> lowerUrl.contains(pattern.toLowerCase()));
    }

    /**
     * Validate that extracted article has required fields
     */
    private boolean isValidArticle(ArticleDTO article) {
        return article != null &&
                article.getTitle() != null && article.getTitle().length() >= 5 &&
                article.getContent() != null && article.getContent().length() >= 50 &&
                article.getUrl() != null && article.getUrl().startsWith("http");
    }

    /**
     * Unescape HTML entities from JSON-LD content
     */
    private String unescapeHtmlEntities(String html) {
        if (html == null) return null;

        return html
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("&apos;", "'")
                .replaceAll("&nbsp;", " ")
                // Convert HTML tags to line breaks for readability
                .replaceAll("<p>", "")
                .replaceAll("</p>", "\n\n")
                .replaceAll("<br[^>]*>", "\n")
                .replaceAll("<[^>]+>", "")
                .trim();
    }
}