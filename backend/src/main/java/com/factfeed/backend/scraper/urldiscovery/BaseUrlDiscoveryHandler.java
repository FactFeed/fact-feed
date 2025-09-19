package com.factfeed.backend.scraper.urldiscovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

@AllArgsConstructor
public abstract class BaseUrlDiscoveryHandler implements UrlDiscoveryHandler {
    // Constants for configuration
    private static final int MAX_CONSECUTIVE_STORED = 10;
    private static final int DEFAULT_WAIT_TIMEOUT_SECONDS = 15;
    private static final int MAX_PAGINATION_PAGES = 50;
    protected final WebDriver driver;

    protected abstract String getBaseUrl();

    protected abstract String getLatestPath();

    protected abstract String getLoadMoreButtonSelector();

    protected abstract String getArticleLinksSelector();

    protected abstract List<String> getSkipUrlsContaining();

    protected abstract List<String> getUrlsAlreadyInDb();

    // Template methods for customization
    protected boolean usesPagination() {
        return false; // Default implementation for load-more button sites
    }

    protected String processUrl(String url) {
        // Default implementation - can be overridden for source-specific processing
        if (url.startsWith("//")) {
            return "https:" + url; // Convert protocol-relative to HTTPS
        } else if (url.startsWith("/")) {
            return getBaseUrl().replaceAll("/$", "") + url; // Convert relative to absolute
        }
        return url;
    }

    @Override
    public List<String> discoveredUrls(int max) {
        Set<String> discoveredUrls = new HashSet<>();
        List<String> result = new ArrayList<>();
        List<String> urlsAlreadyInDb = getUrlsAlreadyInDb();
        List<String> skipUrlsContaining = getSkipUrlsContaining();

        int consecutiveStoredUrls = 0;
        // Stop if we find MAX_CONSECUTIVE_STORED consecutive URLs already in DB

        // Check if this source uses pagination instead of load-more button
        if (usesPagination()) {
            DiscoveryContext context = new DiscoveryContext(max, discoveredUrls, result, urlsAlreadyInDb, skipUrlsContaining);
            return handlePaginationDiscovery(context);
        }

        // Regular load-more button handling for other sources
        driver.get(getBaseUrl() + getLatestPath());

        // Wait for page to load
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(getArticleLinksSelector())));

        while (result.size() < max) {
            // Get article links using the specific selector
            List<WebElement> articleLinks = driver.findElements(By.cssSelector(getArticleLinksSelector()));

            System.out.println("Found " + articleLinks.size() + " total links on page, discovered " + result.size() + " valid URLs so far");

            boolean foundNew = false;
            for (WebElement link : articleLinks) {
                String url = link.getDomAttribute("href");
                if (url != null && !discoveredUrls.contains(url)) {
                    // Check if URL is already in the database (early stopping indicator)
                    if (urlsAlreadyInDb.contains(url)) {
                        consecutiveStoredUrls++;
                        System.out.println("Found stored URL: " + url + " (consecutive count: " + consecutiveStoredUrls + ")");
                        // Early stopping: if we hit too many stored URLs, we've reached stored content
                        if (consecutiveStoredUrls >= MAX_CONSECUTIVE_STORED) {
                            System.out.println("Stopping: hit " + MAX_CONSECUTIVE_STORED + " consecutive stored URLs");
                            return result; // Stop scraping - we've hit stored content
                        }
                        continue;
                    }

                    // Reset counter when we find new URL
                    consecutiveStoredUrls = 0;

                    // Skip URLs containing certain patterns
                    boolean shouldSkip = false;
                    for (String skipPattern : skipUrlsContaining) {
                        if (url.contains(skipPattern)) {
                            shouldSkip = true;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        continue;
                    }

                    // Add valid URLs to result (handle protocol-relative URLs)
                    String processedUrl = processUrl(url);

                    if (processedUrl.startsWith("http") || processedUrl.startsWith("https")) {
                        discoveredUrls.add(processedUrl);
                        result.add(processedUrl);
                        foundNew = true;
                        System.out.println("Added new URL: " + processedUrl + " (total: " + result.size() + ")");

                        if (result.size() >= max) {
                            return result;
                        }
                    }
                }
            }

            // If no new URLs found in this batch but we haven't hit the max, try loading more
            if (!foundNew) {
                System.out.println("No new URLs found in this batch, attempting to load more content...");
                if (!clickLoadMore(driver)) {
                    System.out.println("Failed to load more content or reached end, stopping");
                    break;
                }
            } else {
                // Found new URLs, try to load more to get additional content
                System.out.println("Found new URLs, attempting to load more content...");
                if (!clickLoadMore(driver)) {
                    System.out.println("No more content available to load");
                    break;
                }
            }
        }

        return result;
    }

    protected boolean clickLoadMore(WebDriver driver) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS));

            // Use the specific load more button selector
            WebElement loadMoreButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(getLoadMoreButtonSelector())));

            if (loadMoreButton != null && loadMoreButton.isDisplayed()) {
                // Scroll to the button first to ensure it's in view
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", loadMoreButton);
                // Wait for the button to be clickable after scrolling
                wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(getLoadMoreButtonSelector())));

                // Store current article count to verify new content loaded
                int beforeClickCount = driver.findElements(By.cssSelector(getArticleLinksSelector())).size();

                // Click using JavaScript to ensure it works with modern JS frameworks
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", loadMoreButton);

                // Wait for new content to load by checking for increased article count
                try {
                    wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(
                            By.cssSelector(getArticleLinksSelector()), beforeClickCount));

                    // Additional wait for content stabilization
                    wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(getArticleLinksSelector())));
                    return true;
                } catch (org.openqa.selenium.TimeoutException e) {
                    // Content didn't load within timeout
                    return false;
                }
            }
        } catch (Exception e) {
            // No load more button or error clicking - this is expected at end of content
            System.out.println("Load more failed: " + e.getMessage());
        }
        return false;
    }

    private List<String> handlePaginationDiscovery(DiscoveryContext context) {
        int consecutiveStoredUrls = 0;
        int page = 1;

        while (context.result.size() < context.maxUrls) {
            // Construct pagination URL: https://www.bd-pratidin.com/online/todaynews?page=1
            String pageUrl = getBaseUrl() + getLatestPath() + "?page=" + page;

            try {
                driver.get(pageUrl);

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_WAIT_TIMEOUT_SECONDS));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(getArticleLinksSelector())));

                // Get article links using the specific selector
                List<WebElement> articleLinks = driver.findElements(By.cssSelector(getArticleLinksSelector()));

                System.out.println("Page " + page + ": Found " + articleLinks.size() + " total links, discovered " + context.result.size() + " valid URLs so far");

                // If no articles found on this page, we've reached the end
                if (articleLinks.isEmpty()) {
                    System.out.println("No articles found on page " + page + ", stopping pagination");
                    break;
                }

                boolean foundNewOnThisPage = false;
                for (WebElement link : articleLinks) {
                    String url = link.getDomAttribute("href");
                    if (url != null && !context.discoveredUrls.contains(url)) {
                        // Check if URL is already in the database (early stopping indicator)
                        if (context.urlsAlreadyInDb.contains(url)) {
                            consecutiveStoredUrls++;
                            System.out.println("Found stored URL: " + url + " (consecutive count: " + consecutiveStoredUrls + ")");
                            // Early stopping: if we hit too many stored URLs, we've reached stored content
                            if (consecutiveStoredUrls >= MAX_CONSECUTIVE_STORED) {
                                System.out.println("Stopping: hit " + MAX_CONSECUTIVE_STORED + " consecutive stored URLs");
                                return context.result; // Stop scraping - we've hit stored content
                            }
                            continue;
                        }

                        // Reset counter when we find new URL
                        consecutiveStoredUrls = 0;

                        // Skip URLs containing certain patterns
                        boolean shouldSkip = false;
                        for (String skipPattern : context.skipUrlsContaining) {
                            if (url.contains(skipPattern)) {
                                shouldSkip = true;
                                break;
                            }
                        }
                        if (shouldSkip) {
                            continue;
                        }

                        // Handle relative URLs by making them absolute
                        String processedUrl = processUrl(url);

                        if (processedUrl.startsWith("http") || processedUrl.startsWith("https")) {
                            context.discoveredUrls.add(processedUrl);
                            context.result.add(processedUrl);
                            foundNewOnThisPage = true;
                            System.out.println("Added new URL: " + processedUrl + " (total: " + context.result.size() + ")");

                            if (context.result.size() >= context.maxUrls) {
                                return context.result;
                            }
                        }
                    }
                }

                // If no new URLs found on this page, we might have reached the end
                if (!foundNewOnThisPage) {
                    System.out.println("No new URLs found on page " + page + ", stopping pagination");
                    break;
                }

                page++; // Move to next page

            } catch (Exception e) {
                System.out.println("Error accessing page " + page + ": " + e.getMessage());
                break; // Stop pagination on error
            }
        }

        return context.result;
    }

    // Inner class to encapsulate discovery context and reduce parameter count
    protected static class DiscoveryContext {
        final int maxUrls;
        final Set<String> discoveredUrls;
        final List<String> result;
        final List<String> urlsAlreadyInDb;
        final List<String> skipUrlsContaining;

        DiscoveryContext(int maxUrls, Set<String> discoveredUrls, List<String> result,
                         List<String> urlsAlreadyInDb, List<String> skipUrlsContaining) {
            this.maxUrls = maxUrls;
            this.discoveredUrls = discoveredUrls;
            this.result = result;
            this.urlsAlreadyInDb = urlsAlreadyInDb;
            this.skipUrlsContaining = skipUrlsContaining;
        }
    }
}