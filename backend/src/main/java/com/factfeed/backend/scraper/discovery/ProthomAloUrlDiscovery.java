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
 * URL discovery handler for Prothom Alo - supports multipage navigation
 */
@Slf4j
@Component
public class ProthomAloUrlDiscovery implements UrlDiscoveryHandler {

    private static final String SITE_NAME = "prothom_alo";
    private static final String LATEST_PATH = "/latest";
    private static final String ARTICLE_SELECTOR = ".story_list_category a";
    private static final String NEXT_PAGE_SELECTOR = ".next a";
    private static final int PAGE_LOAD_WAIT_SECONDS = 3;

    @Override
    public List<String> discoverUrls(WebDriver driver, String baseUrl, int maxCount, Set<String> stopUrls) {
        log.info("Starting URL discovery for Prothom Alo, max count: {}", maxCount);

        try {
            // Navigate to latest page
            String latestUrl = baseUrl + LATEST_PATH;
            driver.get(latestUrl);
            log.info("Navigated to: {}", latestUrl);

            // Wait for page to load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            wait.until(webDriver -> !webDriver.findElements(By.cssSelector(ARTICLE_SELECTOR)).isEmpty());

            List<String> discoveredUrls = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int currentPage = 1;

            while (discoveredUrls.size() < maxCount) {
                log.debug("Processing page {} for Prothom Alo", currentPage);

                // Extract URLs from current page
                List<WebElement> articles = driver.findElements(By.cssSelector(ARTICLE_SELECTOR));
                boolean foundNewUrls = false;

                log.debug("Found {} article elements on page {}", articles.size(), currentPage);

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

                // Try to navigate to next page
                if (!navigateToNextPage(driver)) {
                    log.info("No more pages available");
                    break;
                }
                currentPage++;
            }

            log.info("URL discovery completed for Prothom Alo. Discovered {} URLs across {} pages",
                    discoveredUrls.size(), currentPage);
            return discoveredUrls;

        } catch (Exception e) {
            log.error("Error during URL discovery for Prothom Alo", e);
            return new ArrayList<>();
        }
    }

    /**
     * Navigate to the next page of articles
     */
    private boolean navigateToNextPage(WebDriver driver) {
        try {
            WebElement nextPageLink = driver.findElement(By.cssSelector(NEXT_PAGE_SELECTOR));
            if (nextPageLink.isDisplayed() && nextPageLink.isEnabled()) {
                String nextPageUrl = nextPageLink.getDomAttribute("href");
                log.debug("Navigating to next page: {}", nextPageUrl);
                assert nextPageUrl != null;
                driver.get(nextPageUrl);

                // Wait for page to load
                Thread.sleep(PAGE_LOAD_WAIT_SECONDS * 1000);

                // Wait for content to load
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                wait.until(webDriver -> !webDriver.findElements(By.cssSelector(ARTICLE_SELECTOR)).isEmpty());

                return true;
            } else {
                log.debug("Next page link not available");
                return false;
            }
        } catch (Exception e) {
            log.debug("Failed to navigate to next page: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getSiteName() {
        return SITE_NAME;
    }
}