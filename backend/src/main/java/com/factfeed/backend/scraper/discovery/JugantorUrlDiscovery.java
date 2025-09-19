package com.factfeed.backend.scraper.discovery;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

/**
 * URL discovery handler for Jugantor - supports load more button functionality
 */
@Slf4j
@Component
public class JugantorUrlDiscovery implements UrlDiscoveryHandler {

    private static final String SITE_NAME = "jugantor";
    private static final String LATEST_PATH = "/latest";
    private static final String ARTICLE_SELECTOR = ".latest-news-item a";
    private static final String LOAD_MORE_BUTTON = ".load-more-btn";
    private static final int LOAD_WAIT_SECONDS = 3;

    @Override
    public List<String> discoverUrls(WebDriver driver, String baseUrl, int maxCount, Set<String> stopUrls) {
        log.info("Starting URL discovery for Jugantor, max count: {}", maxCount);

        try {
            // Navigate to latest page
            String latestUrl = baseUrl + LATEST_PATH;
            driver.get(latestUrl);
            log.info("Navigated to: {}", latestUrl);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(webDriver -> webDriver.findElements(By.cssSelector(ARTICLE_SELECTOR)).size() > 0);

            List<String> discoveredUrls = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int loadAttempts = 0;

            while (discoveredUrls.size() < maxCount) {
                log.debug("Processing content batch {} for Jugantor", loadAttempts + 1);

                // Extract URLs from current page
                List<WebElement> articles = driver.findElements(By.cssSelector(ARTICLE_SELECTOR));
                boolean foundNewUrls = false;

                log.debug("Found {} article elements in batch {}", articles.size(), loadAttempts + 1);

                for (WebElement article : articles) {
                    try {
                        String url = article.getDomAttribute("href");
                        if (url != null && !url.trim().isEmpty()) {
                            // Ensure full URL
                            if (!url.startsWith("http")) {
                                url = baseUrl + url;
                            }

                            // Check if we've hit existing content (stop condition)
                            if (stopUrls.contains(url)) {
                                log.info("Found existing URL, stopping discovery: {}", url);
                                return discoveredUrls;
                            }

                            // Check if this is a new URL we haven't seen
                            if (!seenUrls.contains(url)) {
                                seenUrls.add(url);
                                discoveredUrls.add(url);
                                foundNewUrls = true;
                                log.debug("Discovered new URL: {}", url);

                                if (discoveredUrls.size() >= maxCount) {
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error extracting URL from article element", e);
                    }
                }

                // If no new URLs found or reached max count, stop
                if (!foundNewUrls || discoveredUrls.size() >= maxCount) {
                    log.info("No more new URLs found or reached max count");
                    break;
                }

                // Try to load more content
                if (!loadMoreContent(driver)) {
                    log.info("No more content to load");
                    break;
                }
                loadAttempts++;
            }

            log.info("URL discovery completed for Jugantor. Discovered {} URLs in {} load attempts",
                    discoveredUrls.size(), loadAttempts + 1);
            return discoveredUrls;

        } catch (Exception e) {
            log.error("Error during URL discovery for Jugantor", e);
            return new ArrayList<>();
        }
    }

    /**
     * Attempt to load more content by clicking the load more button
     */
    private boolean loadMoreContent(WebDriver driver) {
        try {
            WebElement loadMoreBtn = driver.findElement(By.cssSelector(LOAD_MORE_BUTTON));
            if (loadMoreBtn.isDisplayed() && loadMoreBtn.isEnabled()) {
                log.debug("Clicking load more button");
                loadMoreBtn.click();

                // Wait for AJAX response
                Thread.sleep(LOAD_WAIT_SECONDS * 1000);

                // Additional wait for DOM updates
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
                wait.until(webDriver -> {
                    List<WebElement> elements = webDriver.findElements(By.cssSelector(ARTICLE_SELECTOR));
                    return !elements.isEmpty();
                });

                return true;
            } else {
                log.debug("Load more button not available");
                return false;
            }
        } catch (Exception e) {
            log.debug("Failed to click load more button: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }
}