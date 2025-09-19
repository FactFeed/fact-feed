package com.factfeed.backend.scraper.UrlDiscovery;

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
        driver.get(getBaseUrl() + getLatestPath());

        // Wait for page to load
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(getArticleLinksSelector())));

        Set<String> discoveredUrls = new HashSet<>();
        List<String> result = new ArrayList<>();
        List<String> urlsAlreadyInDb = getUrlsAlreadyInDb();
        List<String> skipUrlsContaining = getSkipUrlsContaining();

        int consecutiveStoredUrls = 0;
        final int MAX_CONSECUTIVE_STORED = 5; // Stop if we find 5 consecutive stored URLs

        while (result.size() < max) {
            List<WebElement> articleLinks = driver.findElements(By.cssSelector(getArticleLinksSelector()));

            boolean foundNew = false;
            for (WebElement link : articleLinks) {
                String url = link.getDomAttribute("href");
                if (url != null && !discoveredUrls.contains(url)) {
                    // Check if URL is already in the database (early stopping indicator)
                    if (urlsAlreadyInDb.contains(url)) {
                        consecutiveStoredUrls++;
                        // Early stopping: if we hit too many stored URLs, we've reached stored content
                        if (consecutiveStoredUrls >= MAX_CONSECUTIVE_STORED) {
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

                    discoveredUrls.add(url);
                    result.add(url);
                    foundNew = true;

                    if (result.size() >= max) {
                        return result;
                    }
                }
            }

            if (!foundNew || !clickLoadMore(driver)) {
                break;
            }
        }

        return result;
    }

    protected boolean clickLoadMore(WebDriver driver) {
        try {
            WebElement loadMoreButton = driver.findElement(By.cssSelector(getLoadMoreButtonSelector()));
            if (loadMoreButton.isDisplayed() && loadMoreButton.isEnabled()) {
                loadMoreButton.click();
                Thread.sleep(2000); // Wait for content to load
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            return false;
        } catch (Exception e) {
            // No load more button or error clicking - this is expected at end of content
        }
        return false;
    }
}