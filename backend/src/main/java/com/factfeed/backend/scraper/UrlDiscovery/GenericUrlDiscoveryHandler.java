package com.factfeed.backend.scraper.UrlDiscovery;

import com.factfeed.backend.db.repository.ArticleRepository;
import com.factfeed.backend.scraper.model.NewsSource;
import java.util.List;
import org.openqa.selenium.WebDriver;

public class GenericUrlDiscoveryHandler extends BaseUrlDiscoveryHandler {

    private final ArticleRepository articleRepository;
    private final NewsSource newsSource;

    public GenericUrlDiscoveryHandler(WebDriver driver, ArticleRepository articleRepository, NewsSource newsSource) {
        super(driver);
        this.articleRepository = articleRepository;
        this.newsSource = newsSource;
    }

    @Override
    protected String getBaseUrl() {
        return newsSource.getBaseUrl();
    }

    @Override
    protected String getLatestPath() {
        return newsSource.getLatestPath();
    }

    @Override
    protected String getLoadMoreButtonSelector() {
        return newsSource.getLoadMoreButtonSelector();
    }

    @Override
    protected String getArticleLinksSelector() {
        return newsSource.getArticleLinksSelector();
    }

    @Override
    protected List<String> getSkipUrlsContaining() {
        return newsSource.getSkipUrlsContaining();
    }

    @Override
    protected List<String> getUrlsAlreadyInDb() {
        return articleRepository.findRecentUrlsBySource(newsSource);
    }
}