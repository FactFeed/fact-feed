package com.factfeed.backend.scraper.config;

import com.factfeed.backend.scraper.model.NewsSource;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ArticleExtractor {

    private static final int TIMEOUT_MS = 10000; // 10 seconds
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36";
    private final Gson gson = new Gson();

    // ThreadLocal to store current document for HTML fallback extraction
    private final ThreadLocal<Document> currentDocument = new ThreadLocal<>();

    public ExtractedArticle extractArticle(String url, NewsSource source) {
        try {
            // Fetch the HTML document
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            // Extract from JSON-LD structured data
            return extractFromJsonLd(doc, url, source);

        } catch (IOException e) {
            log.error("Failed to fetch article from {}: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * Extract article data from JSON-LD structured data
     */
    private ExtractedArticle extractFromJsonLd(Document doc, String pageUrl, NewsSource source) {
        // Store document in ThreadLocal for HTML fallback extraction
        currentDocument.set(doc);

        try {
            // Try both standard JSON-LD and regular JSON script tags
            String[] scriptSelectors = {
                    "script[type=application/ld+json]",  // Standard
                    "script[type=application/json]"     // BD Pratidin uses this
            };

            for (String selector : scriptSelectors) {
                Elements ldJsonScripts = doc.select(selector);

                for (Element script : ldJsonScripts) {
                    try {
                        String json = script.data();
                        JsonElement parsed = JsonParser.parseString(json);

                        // Handle both array and object
                        if (parsed.isJsonArray()) {
                            for (JsonElement el : parsed.getAsJsonArray()) {
                                ExtractedArticle article = parseArticleJson(el, pageUrl, source);
                                if (article != null) return article;
                            }
                        } else {
                            ExtractedArticle article = parseArticleJson(parsed, pageUrl, source);
                            if (article != null) return article;
                        }
                    } catch (Exception e) {
                        log.debug("Failed to parse JSON-LD: {}", e.getMessage());
                        // Continue to next script tag
                    }
                }
            }

            log.warn("No valid JSON-LD article found for: {}", pageUrl);
            return null; // No article found in JSON-LD
        } finally {
            // Clean up ThreadLocal to prevent memory leaks
            currentDocument.remove();
        }
    }

    private ExtractedArticle parseArticleJson(JsonElement el, String fallbackUrl, NewsSource source) {
        if (!el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();

        String type = obj.has("@type") ? obj.get("@type").getAsString() : null;
        if (!"Article".equalsIgnoreCase(type) && !"NewsArticle".equalsIgnoreCase(type)) {
            return null;
        }

        try {
            String url = obj.has("url") ? obj.get("url").getAsString() : fallbackUrl;
            String title = extractTitle(obj, source);
            String author = extractAuthor(obj, source);
            String content = extractContent(obj, source);
            String category = extractCategory(obj, source);
            String tags = extractTags(obj, source);
            String imageUrl = extractImageUrl(obj, source);
            String description = extractDescription(obj, source);

            LocalDateTime publishedAt = parseJsonLdDate(obj, "datePublished");
            LocalDateTime updatedAt = parseJsonLdDate(obj, "dateModified");

            // Clean HTML entities from content if needed
            if (content != null) {
                content = cleanHtmlEntities(content);
            }

            return ExtractedArticle.builder()
                    .url(url)
                    .title(cleanText(title))
                    .author(author)
                    .content(cleanText(content))
                    .category(category)
                    .tags(tags)
                    .imageUrl(imageUrl)
                    .description(description)
                    .articlePublishedAt(publishedAt)
                    .articleUpdatedAt(updatedAt)
                    .source(source)
                    .build();

        } catch (Exception e) {
            log.debug("Error parsing JSON-LD article: {}", e.getMessage());
            return null;
        }
    }

    private String extractTitle(JsonObject obj, NewsSource source) {
        // Try different title fields based on source
        String[] titleFields = getTitleFields(source);
        for (String field : titleFields) {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                return obj.get(field).getAsString();
            }
        }
        return null;
    }

    private String extractAuthor(JsonObject obj, NewsSource source) {
        if (!obj.has("author")) return null;

        JsonElement authorEl = obj.get("author");
        if (authorEl.isJsonArray() && authorEl.getAsJsonArray().size() > 0) {
            JsonObject auth = authorEl.getAsJsonArray().get(0).getAsJsonObject();
            return auth.has("name") ? auth.get("name").getAsString() : null;
        } else if (authorEl.isJsonObject()) {
            JsonObject auth = authorEl.getAsJsonObject();
            return auth.has("name") ? auth.get("name").getAsString() : null;
        }

        return null;
    }

    private String extractContent(JsonObject obj, NewsSource source) {
        String[] contentFields = getContentFields(source);
        String content = null;

        for (String field : contentFields) {
            if (obj.has(field) && !obj.get(field).isJsonNull()) {
                content = obj.get(field).getAsString();
                break;
            }
        }

        // For BD Pratidin, check if JSON-LD content is truncated and fallback to HTML
        if (source == NewsSource.BDPROTIDIN && (content == null || content.trim().endsWith("..."))) {
            String htmlContent = extractBdProtidnContentFromHtml();
            if (htmlContent != null && !htmlContent.isEmpty()) {
                return htmlContent;
            }
        }

        return content;
    }

    private String extractCategory(JsonObject obj, NewsSource source) {
        if (obj.has("articleSection")) {
            return obj.get("articleSection").getAsString();
        }
        return null;
    }

    private String extractTags(JsonObject obj, NewsSource source) {
        if (obj.has("keywords")) {
            return obj.get("keywords").getAsString();
        }
        return null;
    }

    private String extractDescription(JsonObject obj, NewsSource source) {
        String description = null;
        if (obj.has("description")) {
            description = obj.get("description").getAsString();
        }

        // For BD Pratidin, check if JSON-LD description is truncated and fallback to HTML
        if (source == NewsSource.BDPROTIDIN && (description == null || description.trim().endsWith("..."))) {
            String htmlContent = extractBdProtidnContentFromHtml();
            if (htmlContent != null && !htmlContent.isEmpty()) {
                // Use first paragraph as description
                String[] paragraphs = htmlContent.split("\n\n");
                if (paragraphs.length > 0) {
                    description = paragraphs[0].trim();
                    // Limit description to first 200 characters
                    if (description.length() > 200) {
                        description = description.substring(0, 200) + "...";
                    }
                }
            }
        }

        return description;
    }

    private String extractImageUrl(JsonObject obj, NewsSource source) {
        if (!obj.has("image")) return null;

        JsonElement imageEl = obj.get("image");
        if (imageEl.isJsonObject()) {
            JsonObject imgObj = imageEl.getAsJsonObject();
            return imgObj.has("url") ? imgObj.get("url").getAsString() : null;
        } else if (imageEl.isJsonArray() && imageEl.getAsJsonArray().size() > 0) {
            JsonObject imgObj = imageEl.getAsJsonArray().get(0).getAsJsonObject();
            return imgObj.has("url") ? imgObj.get("url").getAsString() : null;
        }

        return null;
    }

    private LocalDateTime parseJsonLdDate(JsonObject obj, String field) {
        if (!obj.has(field)) return null;

        try {
            String dateStr = obj.get(field).getAsString();

            // Handle BD Pratidin's custom date format: "03:12 AM, September 2025, Saturday"
            if (dateStr.matches("\\d{1,2}:\\d{2} [AP]M, [A-Za-z]+ \\d{4}, [A-Za-z]+")) {
                return parseBdProtidnDate(dateStr);
            }

            // Try different date formats based on common patterns
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ISO_OFFSET_DATE_TIME,     // 2024-12-16T16:34:07+06:00
                    DateTimeFormatter.ISO_ZONED_DATE_TIME,      // 2024-12-16T16:33:30.000Z
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME,      // 2024-12-16T16:33:30
                    DateTimeFormatter.ISO_INSTANT              // 2024-12-16T16:33:30.000Z
            };

            for (DateTimeFormatter formatter : formatters) {
                try {
                    if (formatter == DateTimeFormatter.ISO_INSTANT ||
                            formatter == DateTimeFormatter.ISO_ZONED_DATE_TIME) {
                        // For these formatters, parse as ZonedDateTime then convert
                        return ZonedDateTime.parse(dateStr, formatter).toLocalDateTime();
                    } else {
                        return LocalDateTime.parse(dateStr, formatter);
                    }
                } catch (DateTimeParseException ignored) {
                    // Try next formatter
                }
            }

            log.debug("Could not parse date string, using current time: {}", dateStr);
            return LocalDateTime.now(); // Fallback to current time for unparseable dates

        } catch (Exception e) {
            log.debug("Failed to parse date field {}, using current time: {}", field, e.getMessage());
            return LocalDateTime.now(); // Fallback to current time
        }
    }

    /**
     * Parse BD Pratidin's specific date format: "03:12 AM, September 2025, Saturday"
     */
    private LocalDateTime parseBdProtidnDate(String dateStr) {
        try {
            // Split the date string: "03:12 AM, September 2025, Saturday"
            String[] parts = dateStr.split(", ");
            if (parts.length != 3) {
                log.debug("Unexpected BD Pratidin date format: {}", dateStr);
                return LocalDateTime.now();
            }

            String timePart = parts[0];        // "03:12 AM"
            String monthYearPart = parts[1];   // "September 2025"
            String dayPart = parts[2];         // "Saturday"

            // Parse month and year
            String[] monthYear = monthYearPart.split(" ");
            if (monthYear.length != 2) {
                log.debug("Could not parse month/year from: {}", monthYearPart);
                return LocalDateTime.now();
            }

            String monthName = monthYear[0];
            int year = Integer.parseInt(monthYear[1]);

            // Convert month name to number
            int month = getMonthNumber(monthName);
            if (month == -1) {
                log.debug("Unknown month name: {}", monthName);
                return LocalDateTime.now();
            }

            // Parse time
            String[] timeAmPm = timePart.split(" ");
            if (timeAmPm.length != 2) {
                log.debug("Could not parse time from: {}", timePart);
                return LocalDateTime.now();
            }

            String[] hourMin = timeAmPm[0].split(":");
            if (hourMin.length != 2) {
                log.debug("Could not parse hour:minute from: {}", timeAmPm[0]);
                return LocalDateTime.now();
            }

            int hour = Integer.parseInt(hourMin[0]);
            int minute = Integer.parseInt(hourMin[1]);
            String amPm = timeAmPm[1];

            // Convert to 24-hour format
            if ("AM".equals(amPm) && hour == 12) {
                hour = 0;
            } else if ("PM".equals(amPm) && hour != 12) {
                hour += 12;
            }

            // Since BD Pratidin doesn't provide the day of month, use current day
            // This is a reasonable assumption for news articles (published today)
            int dayOfMonth = LocalDateTime.now().getDayOfMonth();

            LocalDateTime parsed = LocalDateTime.of(year, month, dayOfMonth, hour, minute);
            log.debug("Successfully parsed BD Pratidin date: {} -> {}", dateStr, parsed);
            return parsed;

        } catch (Exception e) {
            log.debug("Error parsing BD Pratidin date '{}': {}", dateStr, e.getMessage());
            return LocalDateTime.now();
        }
    }

    /**
     * Convert month name to number (1-12)
     */
    private int getMonthNumber(String monthName) {
        switch (monthName.toLowerCase()) {
            case "january":
                return 1;
            case "february":
                return 2;
            case "march":
                return 3;
            case "april":
                return 4;
            case "may":
                return 5;
            case "june":
                return 6;
            case "july":
                return 7;
            case "august":
                return 8;
            case "september":
                return 9;
            case "october":
                return 10;
            case "november":
                return 11;
            case "december":
                return 12;
            default:
                return -1;
        }
    }

    /**
     * Source-specific configuration methods
     */
    private String[] getTitleFields(NewsSource source) {
        switch (source) {
            case PROTHOMALO:
                return new String[]{"headline", "name", "title"};
            case ITTEFAQ:
                return new String[]{"headline", "name", "title"};
            case SAMAKAL:
                return new String[]{"headline", "name", "title"};
            case JUGANTOR:
                return new String[]{"headline", "name", "title"};
            case BDPROTIDIN:
                return new String[]{"headline", "name", "title"};
            default:
                return new String[]{"headline", "name", "title"};
        }
    }

    private String[] getContentFields(NewsSource source) {
        switch (source) {
            case PROTHOMALO:
                return new String[]{"articleBody", "description", "text"};
            case ITTEFAQ:
                return new String[]{"articleBody", "description", "text"};
            case SAMAKAL:
                return new String[]{"articleBody", "description", "text"};
            case JUGANTOR:
                return new String[]{"articleBody", "description", "text"};
            case BDPROTIDIN:
                return new String[]{"articleBody", "description", "text"};
            default:
                return new String[]{"articleBody", "description", "text"};
        }
    }

    private String cleanText(String text) {
        if (text == null) return null;

        // Remove extra whitespace and normalize
        return text.replaceAll("\\s+", " ")
                .replaceAll("[\\r\\n]+", "\n")
                .trim();
    }

    private String cleanHtmlEntities(String text) {
        if (text == null) return null;

        return text.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#039;", "'")  // Additional single quote escape sequence
                .replace("&ndash;", "–")
                .replaceAll("<[^>]*>", "") // Remove HTML tags
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Extract content from BD Pratidin's HTML <article> tag when JSON-LD is truncated
     */
    private String extractBdProtidnContentFromHtml() {
        Document doc = currentDocument.get();
        if (doc == null) {
            return null;
        }

        try {
            // Select the article element (HTML tag, not class)
            Elements articleElements = doc.select("article");
            if (articleElements.isEmpty()) {
                log.debug("No <article> element found for BD Pratidin HTML extraction");
                return null;
            }

            Element article = articleElements.first();

            // Extract all paragraph elements within the article
            Elements paragraphs = article.select("p");
            if (paragraphs.isEmpty()) {
                log.debug("No <p> elements found in <article> for BD Pratidin");
                return null;
            }

            StringBuilder content = new StringBuilder();
            for (Element paragraph : paragraphs) {
                String text = paragraph.text().trim();
                if (!text.isEmpty()) {
                    content.append(text).append("\n\n");
                }
            }

            String extractedContent = content.toString().trim();
            log.info("✅ BD Pratidin HTML extraction: {} characters from {} paragraphs",
                    extractedContent.length(), paragraphs.size());
            return extractedContent;

        } catch (Exception e) {
            log.error("❌ Error extracting BD Pratidin content from HTML <article>: {}", e.getMessage());
            return null;
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedArticle {
        private String url;
        private String title;
        private String author;
        private String authorLocation;
        private LocalDateTime articlePublishedAt;
        private LocalDateTime articleUpdatedAt;
        private String imageUrl;
        private String imageCaption;
        private String content;
        private String description;
        private String category;
        private String tags;
        private NewsSource source;
    }
}