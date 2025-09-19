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
    protected final WebDriver driver;

    protected abstract String getBaseUrl();

    protected abstract String getLatestPath();

    protected abstract String getLoadMoreButtonSelector();

    protected abstract String getArticleLinksSelector();

    protected abstract List<String> getSkipUrlsContaining();

    protected abstract List<String> getUrlsAlreadyInDb();

    @Override
    public List<String> discoveredUrls(int max) {
        Set<String> discoveredUrls = new HashSet<>();
        List<String> result = new ArrayList<>();
        List<String> urlsAlreadyInDb = getUrlsAlreadyInDb();
        List<String> skipUrlsContaining = getSkipUrlsContaining();

        int consecutiveStoredUrls = 0;
        final int MAX_CONSECUTIVE_STORED = 10; // Stop if we find 10 consecutive stored URLs

        // Check if this is BD Pratidin which uses pagination
        if (getBaseUrl().contains("bd-pratidin.com")) {
            return handlePaginationDiscovery(max, discoveredUrls, result, urlsAlreadyInDb, skipUrlsContaining);
        }

        // Regular load-more button handling for other sources
        driver.get(getBaseUrl() + getLatestPath());

        // Wait for page to load
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
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
                    String processedUrl = url;
                    // ittefaq specific fix
                    if (url.startsWith("//")) {
                        processedUrl = "https:" + url; // Convert protocol-relative to HTTPS
                    }

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
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));

            // Use the specific load more button selector
            WebElement loadMoreButton = wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(getLoadMoreButtonSelector())));

            if (loadMoreButton != null && loadMoreButton.isDisplayed()) {
                // Scroll to the button first to ensure it's in view
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", loadMoreButton);
                Thread.sleep(1000); // Wait for scroll to complete

                // Store current article count to verify new content loaded
                int beforeClickCount = driver.findElements(By.cssSelector(getArticleLinksSelector())).size();

                // Click using JavaScript to ensure it works with modern JS frameworks
                ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", loadMoreButton);

                // Wait for new content to load - check for increased article count
                for (int i = 0; i < 15; i++) { // Wait up to 15 seconds
                    Thread.sleep(1000);
                    int afterClickCount = driver.findElements(By.cssSelector(getArticleLinksSelector())).size();
                    if (afterClickCount > beforeClickCount) {
                        // New content loaded successfully
                        Thread.sleep(2000); // Additional wait for content stabilization
                        return true;
                    }
                }

                // If no new content after clicking, assume no more content available
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            return false;
        } catch (Exception e) {
            // No load more button or error clicking - this is expected at end of content
            System.out.println("Load more failed: " + e.getMessage());
        }
        return false;
    }

    private List<String> handlePaginationDiscovery(int max, Set<String> discoveredUrls, List<String> result,
                                                   List<String> urlsAlreadyInDb, List<String> skipUrlsContaining) {
        int consecutiveStoredUrls = 0;
        final int MAX_CONSECUTIVE_STORED = 10;
        int page = 1;

        while (result.size() < max) {
            // Construct pagination URL: https://www.bd-pratidin.com/online/todaynews?page=1
            String pageUrl = getBaseUrl() + getLatestPath() + "?page=" + page;

            try {
                driver.get(pageUrl);

                // Wait for page to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(getArticleLinksSelector())));

                // Get article links using the specific selector
                List<WebElement> articleLinks = driver.findElements(By.cssSelector(getArticleLinksSelector()));

                System.out.println("Page " + page + ": Found " + articleLinks.size() + " total links, discovered " + result.size() + " valid URLs so far");

                // If no articles found on this page, we've reached the end
                if (articleLinks.isEmpty()) {
                    System.out.println("No articles found on page " + page + ", stopping pagination");
                    break;
                }

                boolean foundNewOnThisPage = false;
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

                        // Handle relative URLs by making them absolute
                        String processedUrl = url;
                        if (url.startsWith("/")) {
                            processedUrl = getBaseUrl().replaceAll("/$", "") + url; // Remove trailing slash from base and add URL
                        } else if (url.startsWith("//")) {
                            processedUrl = "https:" + url; // Convert protocol-relative to HTTPS
                        }

                        if (processedUrl.startsWith("http") || processedUrl.startsWith("https")) {
                            discoveredUrls.add(processedUrl);
                            result.add(processedUrl);
                            foundNewOnThisPage = true;
                            System.out.println("Added new URL: " + processedUrl + " (total: " + result.size() + ")");

                            if (result.size() >= max) {
                                return result;
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

        return result;
    }
}