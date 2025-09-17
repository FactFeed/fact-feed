package com.factfeed.backend.model.enums;

import lombok.Getter;

@Getter
public enum NewsSource {
    PROTHOMALO("https://www.prothomalo.com/"),
    ITTEFAQ("https://www.ittefaq.com.bd/");

    private final String baseUrl;

    NewsSource(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * Find NewsSource by URL
     */
    public static NewsSource fromUrl(String url) {
        if (url == null) return null;
        for (NewsSource source : values()) {
            if (source.matchesUrl(url)) {
                return source;
            }
        }
        return null;
    }

    /**
     * Find NewsSource by name (case-insensitive)
     */
    public static NewsSource fromName(String name) {
        if (name == null) return null;
        String normalized = name.replaceAll("[^A-Za-z]", "").toUpperCase();
        for (NewsSource source : values()) {
            if (source.name().equals(normalized)) {
                return source;
            }
        }
        return null;
    }

    /**
     * Get the domain name from the base URL
     */
    public String getDomain() {
        if (baseUrl == null) return null;
        return baseUrl.replaceAll("https?://", "").replaceAll("/.*", "");
    }

    /**
     * Check if a URL belongs to this news source
     */
    public boolean matchesUrl(String url) {
        return url != null && url.toLowerCase().contains(getDomain().toLowerCase());
    }

    /**
     * Get the source name in lowercase for YAML key matching
     */
    public String getYamlKey() {
        switch (this) {
            case PROTHOMALO:
                return "prothomalo";
            case ITTEFAQ:
                return "dailyittefaq";
            default:
                return this.name().toLowerCase().replace("_", "");
        }
    }
}