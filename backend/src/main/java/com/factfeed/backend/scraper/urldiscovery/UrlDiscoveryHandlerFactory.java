package com.factfeed.backend.scraper.urldiscovery;

import com.factfeed.backend.db.repository.ArticleRepository;
import com.factfeed.backend.scraper.model.NewsSource;
import lombok.AllArgsConstructor;
import org.openqa.selenium.WebDriver;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UrlDiscoveryHandlerFactory {

    private final ArticleRepository articleRepository;

    public UrlDiscoveryHandler createHandler(WebDriver driver, NewsSource newsSource) {
        return new GenericUrlDiscoveryHandler(driver, articleRepository, newsSource);
    }

    public UrlDiscoveryHandler createProthomAloHandler(WebDriver driver) {
        return createHandler(driver, NewsSource.PROTHOMALO);
    }

    public UrlDiscoveryHandler createIttefaqHandler(WebDriver driver) {
        return createHandler(driver, NewsSource.ITTEFAQ);
    }

    public UrlDiscoveryHandler createSamakalHandler(WebDriver driver) {
        return createHandler(driver, NewsSource.SAMAKAL);
    }

    public UrlDiscoveryHandler createJugantorHandler(WebDriver driver) {
        return createHandler(driver, NewsSource.JUGANTOR);
    }

    public UrlDiscoveryHandler createBdProtidinnHandler(WebDriver driver) {
        return createHandler(driver, NewsSource.BDPROTIDIN);
    }
}