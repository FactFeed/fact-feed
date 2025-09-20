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
                                ExtractedArticle article = parseArticleJson(el, pageUrl, source, doc);
                                if (article != null) return article;
                            }
                        } else {
                            ExtractedArticle article = parseArticleJson(parsed, pageUrl, source, doc);
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
        } catch (Exception e) {
            log.error("Error extracting from JSON-LD: {}", e.getMessage());
            return null;
        }
    }

    private ExtractedArticle parseArticleJson(JsonElement el, String fallbackUrl, NewsSource source, Document doc) {
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
            String content = extractContent(obj, source, doc);
            String category = extractCategory(obj, source);
            String tags = extractTags(obj, source);
            String imageUrl = extractImageUrl(obj, source);
            String description = extractDescription(obj, source, doc);

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

    private String extractContent(JsonObject obj, NewsSource source, Document doc) {
        // For Samakal, always use HTML extraction as primary method since JSON-LD is always truncated
        if (source == NewsSource.SAMAKAL) {
            String htmlContent = extractSamakalContentFromHtml(doc);
            if (htmlContent != null && !htmlContent.isEmpty()) {
                return htmlContent;
            }
            // Fallback to JSON-LD if HTML extraction fails
            log.warn("Samakal HTML extraction failed, falling back to truncated JSON-LD content");
        }

        // For Jugantor, always use HTML extraction as primary method since content is loaded via AJAX
        if (source == NewsSource.JUGANTOR) {
            String htmlContent = extractJugantorContentFromHtml(doc);
            if (htmlContent != null && !htmlContent.isEmpty()) {
                return htmlContent;
            }
            // Fallback to JSON-LD if HTML extraction fails
            log.warn("Jugantor HTML extraction failed, falling back to JSON-LD description");
        }

        // For other sources, use JSON-LD as primary method
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
            String htmlContent = extractBdProtidnContentFromHtml(doc);
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

    private String extractDescription(JsonObject obj, NewsSource source, Document doc) {
        // For Samakal, always use HTML extraction as primary method since JSON-LD description is often truncated
        if (source == NewsSource.SAMAKAL) {
            String htmlContent = extractSamakalContentFromHtml(doc);
            if (htmlContent != null && !htmlContent.isEmpty()) {
                // Use first paragraph as description
                String[] paragraphs = htmlContent.split("\n\n");
                if (paragraphs.length > 0) {
                    String description = paragraphs[0].trim();
                    // Limit description to first 200 characters
                    if (description.length() > 200) {
                        description = description.substring(0, 200) + "...";
                    }
                    return description;
                }
            }
            // Fallback to JSON-LD if HTML extraction fails
            log.warn("Samakal HTML extraction failed for description, falling back to JSON-LD");
        }

        // For Jugantor, always use HTML extraction as primary method since JSON-LD description is often brief
        if (source == NewsSource.JUGANTOR) {
            String htmlContent = extractJugantorContentFromHtml(doc);
            if (htmlContent != null && !htmlContent.isEmpty()) {
                // Use first paragraph as description
                String[] paragraphs = htmlContent.split("\n\n");
                if (paragraphs.length > 0) {
                    String description = paragraphs[0].trim();
                    // Limit description to first 200 characters
                    if (description.length() > 200) {
                        description = description.substring(0, 200) + "...";
                    }
                    return description;
                }
            }
            // Fallback to JSON-LD if HTML extraction fails
            log.warn("Jugantor HTML extraction failed for description, falling back to JSON-LD");
        }

        // For other sources, use JSON-LD as primary method
        String description = null;
        if (obj.has("description")) {
            description = obj.get("description").getAsString();
        }

        // For BD Pratidin, check if JSON-LD description is truncated and fallback to HTML
        if (source == NewsSource.BDPROTIDIN && (description == null || description.trim().endsWith("..."))) {
            String htmlContent = extractBdProtidnContentFromHtml(doc);
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

            // Handle Samakal's date format: "2025-09-19 07:42:32"
            if (dateStr.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
                try {
                    DateTimeFormatter samakalFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return LocalDateTime.parse(dateStr, samakalFormatter);
                } catch (DateTimeParseException e) {
                    log.debug("Failed to parse Samakal date format: {}", dateStr);
                }
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
     * Parse BD Pratidin's specific date format: "03:12 AM, September 25, Saturday" or "03:12 AM, September 2025, Saturday"
     */
    private LocalDateTime parseBdProtidnDate(String dateStr) {
        try {
            // Split the date string: "03:12 AM, September 25, Saturday" or "03:12 AM, September 2025, Saturday"
            String[] parts = dateStr.split(", ");
            if (parts.length != 3) {
                log.debug("Unexpected BD Pratidin date format: {}", dateStr);
                return LocalDateTime.now();
            }

            String timePart = parts[0];        // "03:12 AM"
            String monthDayOrYearPart = parts[1];   // "September 25" or "September 2025"
            String dayPart = parts[2];         // "Saturday"

            // Parse month and day/year
            String[] monthDayOrYear = monthDayOrYearPart.split(" ");
            if (monthDayOrYear.length != 2) {
                log.debug("Could not parse month/day/year from: {}", monthDayOrYearPart);
                return LocalDateTime.now();
            }

            String monthName = monthDayOrYear[0];
            String secondPart = monthDayOrYear[1];

            int month = getMonthNumber(monthName);
            if (month == -1) {
                log.debug("Unknown month name: {}", monthName);
                return LocalDateTime.now();
            }

            int dayOfMonth = -1;
            int year = -1;

            // Try to parse day or year
            if (secondPart.matches("\\d{1,2}")) {
                // Format: "September 25" - day is provided
                dayOfMonth = Integer.parseInt(secondPart);
                year = LocalDateTime.now().getYear(); // Use current year
            } else if (secondPart.matches("\\d{4}")) {
                // Format: "September 2025" - year is provided, day is missing
                year = Integer.parseInt(secondPart);
                // Since day is missing, we need to estimate it
                // For news articles, use current day or yesterday if time suggests previous day
                LocalDateTime now = LocalDateTime.now();
                dayOfMonth = now.getDayOfMonth();

                // Parse time to see if it suggests a previous day
                String[] timeAmPm = timePart.split(" ");
                if (timeAmPm.length == 2) {
                    String[] hourMin = timeAmPm[0].split(":");
                    if (hourMin.length == 2) {
                        try {
                            int hour = Integer.parseInt(hourMin[0]);
                            String amPm = timeAmPm[1];

                            // Convert to 24-hour format for comparison
                            if ("AM".equals(amPm) && hour == 12) {
                                hour = 0;
                            } else if ("PM".equals(amPm) && hour != 12) {
                                hour += 12;
                            }

                            // If it's early morning and current time is later in the day,
                            // article might be from yesterday
                            if (hour < 6 && now.getHour() > 12) {
                                dayOfMonth = now.minusDays(1).getDayOfMonth();
                                // Handle month rollover
                                if (dayOfMonth > now.getDayOfMonth()) {
                                    month = now.minusDays(1).getMonthValue();
                                    year = now.minusDays(1).getYear();
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Fall back to current day
                        }
                    }
                }
                log.debug("BD Pratidin date missing day, estimated: day={}, month={}, year={}",
                        dayOfMonth, month, year);
            } else {
                log.debug("Could not parse day/year from: {}", secondPart);
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
    private String extractBdProtidnContentFromHtml(Document doc) {
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

    /**
     * Extract content from Samakal's HTML when JSON-LD is truncated
     * Uses .dNewsDesc#contentDetails > p selector to get full article content
     */
    private String extractSamakalContentFromHtml(Document doc) {
        if (doc == null) {
            return null;
        }

        try {
            // Select the content div using both class and id for precision
            Elements contentElements = doc.select("div.dNewsDesc#contentDetails");
            if (contentElements.isEmpty()) {
                // Fallback to just class selector
                contentElements = doc.select(".dNewsDesc");
                if (contentElements.isEmpty()) {
                    // Try alternative selectors that might exist on Samakal
                    contentElements = doc.select("div[id*=content], div[class*=content], div[class*=article]");
                    if (contentElements.isEmpty()) {
                        log.debug("No content element found for Samakal HTML extraction");
                        return null;
                    }
                    log.debug("Using fallback selector for Samakal content extraction");
                }
            }

            Element contentDiv = contentElements.first();

            // Extract all paragraph elements within the content div, excluding ads
            Elements paragraphs = contentDiv.select("p");
            if (paragraphs.isEmpty()) {
                log.debug("No <p> elements found in content div for Samakal");
                return null;
            }

            StringBuilder content = new StringBuilder();
            int validParagraphs = 0;
            for (Element paragraph : paragraphs) {
                String text = paragraph.text().trim();
                // Skip empty paragraphs and ad-related content
                if (!text.isEmpty() && 
                    !text.toLowerCase().contains("googletag") && 
                    !text.toLowerCase().contains("advertisement") &&
                    !text.toLowerCase().contains("div-gpt-ad") &&
                    text.length() > 10) { // Skip very short paragraphs that might be ads/metadata
                    content.append(text).append("\n\n");
                    validParagraphs++;
                }
            }

            String extractedContent = content.toString().trim();
            if (extractedContent.isEmpty()) {
                log.warn("No valid content extracted from Samakal HTML paragraphs (found {} total paragraphs)", paragraphs.size());
                return null;
            }

            log.info("✅ Samakal HTML extraction: {} characters from {} valid paragraphs (out of {} total)",
                    extractedContent.length(), validParagraphs, paragraphs.size());
            return extractedContent;

        } catch (Exception e) {
            log.error("❌ Error extracting Samakal content from HTML: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract content from Jugantor's HTML when JSON-LD doesn't include articleBody
     * Uses .desktopDetailBody > p selector to get full article content
     */
    private String extractJugantorContentFromHtml(Document doc) {
        if (doc == null) {
            return null;
        }

        try {
            // Select the content div using the desktop detail body class
            Elements contentElements = doc.select(".desktopDetailBody");
            if (contentElements.isEmpty()) {
                // Try alternative selectors that might exist on Jugantor
                contentElements = doc.select("div[class*=detail], div[class*=content], div[class*=body]");
                if (contentElements.isEmpty()) {
                    log.debug("No .desktopDetailBody element found for Jugantor HTML extraction");
                    return null;
                }
                log.debug("Using fallback selector for Jugantor content extraction");
            }

            Element contentDiv = contentElements.first();

            // Extract all paragraph elements within the content div
            Elements paragraphs = contentDiv.select("p");
            if (paragraphs.isEmpty()) {
                log.debug("No <p> elements found in .desktopDetailBody for Jugantor");
                return null;
            }

            StringBuilder content = new StringBuilder();
            int validParagraphs = 0;
            for (Element paragraph : paragraphs) {
                String text = paragraph.text().trim();
                // Skip empty paragraphs and ad-related content
                if (!text.isEmpty() && 
                    !text.toLowerCase().contains("googletag") && 
                    !text.toLowerCase().contains("advertisement") &&
                    !text.toLowerCase().contains("div-gpt-ad") &&
                    !text.toLowerCase().contains("loadajaxdetail") &&
                    text.length() > 10) { // Skip very short paragraphs that might be ads/metadata
                    content.append(text).append("\n\n");
                    validParagraphs++;
                }
            }

            String extractedContent = content.toString().trim();
            if (extractedContent.isEmpty()) {
                log.warn("No valid content extracted from Jugantor HTML paragraphs (found {} total paragraphs)", paragraphs.size());
                return null;
            }

            log.info("✅ Jugantor HTML extraction: {} characters from {} valid paragraphs (out of {} total)",
                    extractedContent.length(), validParagraphs, paragraphs.size());
            return extractedContent;

        } catch (Exception e) {
            log.error("❌ Error extracting Jugantor content from HTML: {}", e.getMessage());
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