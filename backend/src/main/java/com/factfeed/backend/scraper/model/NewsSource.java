package com.factfeed.backend.scraper.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum NewsSource {
    PROTHOMALO("prothom_alo", "Prothom Alo"),
    ITTEFAQ("ittefaq", "The Daily Ittefaq"),
    SAMAKAL("samakal", "Samakal"),
    JUGANTOR("jugantor", "Jugantor"),
    BDPROTIDIN("bd_protidin", "Bangladesh Protidin");

    private final String code;
    private final String displayName;

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
        if (displayName == null) throw new IllegalArgumentException("code cannot be null");
        for (NewsSource source : values()) {
            if (source.displayName.equalsIgnoreCase(displayName)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Display name " + displayName + " is not a valid NewsSource");
    }
}