package com.factfeed.backend.scraper.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NewsSource {
    PROTHOMALO("prothom_alo", "Prothom Alo", "https://www.prothomalo.com/", "collection/latest", ".more, .load-more-content", "a.title-link", List.of("video", "ampstories", "gallery", "photo", "lifestyle")),
    ITTEFAQ("ittefaq", "The Daily Ittefaq", "https://www.ittefaq.com.bd/", "latest-news", ".ajax_load_btn", "a.link_overlay", List.of("video", "gallery")),
    SAMAKAL("samakal", "Samakal", "https://samakal.com/", "latest/news", ".load-more-data", "div.CatListNews > a", List.of("video", "gallery")),
    JUGANTOR("jugantor", "Jugantor", "https://www.jugantor.com/", "latest", ".clickLoadMore", "a.linkOverlay", List.of("video", "gallery")),
    BDPROTIDIN("bd_protidin", "Bangladesh Protidin", "https://www.bd-pratidin.com/", "online/todaynews", ".pagination .next", "a.stretched-link", List.of("video", "gallery"));

    private final String code;
    private final String displayName;
    private final String baseUrl;
    private final String latestPath;
    private final String loadMoreButtonSelector;
    private final String articleLinksSelector;
    private final List<String> skipUrlsContaining;

    public static NewsSource fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("code cannot be null");
        for (NewsSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Code " + code + " is not a valid NewsSource");
    }

    public static NewsSource fromDisplayName(String displayName) {
        if (displayName == null) throw new IllegalArgumentException("displayName cannot be null");
        for (NewsSource source : values()) {
            if (source.displayName.equalsIgnoreCase(displayName)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Display name " + displayName + " is not a valid NewsSource");
    }
}