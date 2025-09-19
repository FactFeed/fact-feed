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
 * URL discovery handler for Samakal - supports dual loading (button + infinite scroll)
 */
@Slf4j
@Component
public class SamakalUrlDiscovery implements UrlDiscoveryHandler {

    private static final String SITE_NAME = "samakal";
    private static final String LATEST_PATH = "/latest/news";
    private static final String ARTICLE_SELECTOR = ".col-lg-6 .CatListNews a";
    private static final String LOAD_MORE_BUTTON = ".load-more-data";
    private static final int LOAD_WAIT_SECONDS = 3;

    @Override
    public List<String> discoverUrls(WebDriver driver, String baseUrl, int maxCount, Set<String> stopUrls) {
        log.info("Starting URL discovery for Samakal, max count: {}", maxCount);

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

            while (discoveredUrls.size() < maxCount) {
                // Extract URLs from current page
                List<WebElement> articles = driver.findElements(By.cssSelector(ARTICLE_SELECTOR));
                boolean foundNewUrls = false;

                log.debug("Found {} article elements on page", articles.size());

                for (WebElement article : articles) {
                    try {
                        String url = article.getDomAttribute("href");
                        if (url != null && !url.trim().isEmpty()) {
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
            }

            log.info("URL discovery completed for Samakal. Discovered {} URLs", discoveredUrls.size());
            return discoveredUrls;

        } catch (Exception e) {
            log.error("Error during URL discovery for Samakal", e);
            return new ArrayList<>();
        }
    }

    /**
     * Attempt to load more content by clicking the "আরও" button
     */
    private boolean loadMoreContent(WebDriver driver) {
        try {
            WebElement loadMoreBtn = driver.findElement(By.cssSelector(LOAD_MORE_BUTTON));
            if (loadMoreBtn.isDisplayed() && loadMoreBtn.isEnabled()) {
                log.debug("Clicking load more button");
                loadMoreBtn.click();

                // Wait for AJAX response
                Thread.sleep(LOAD_WAIT_SECONDS * 1000);
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