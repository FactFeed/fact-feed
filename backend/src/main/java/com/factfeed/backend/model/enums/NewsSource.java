package com.factfeed.backend.model.enums;

public enum NewsSource {
    PROTHOM_ALO("prothom_alo", "Prothom Alo"),
    ITTEFAQ("ittefaq", "The Daily Ittefaq"),
    SAMAKAL("samakal", "Samakal"),
    JUGANTOR("jugantor", "Jugantor"),
    JANAKANTHA("janakantha", "Janakantha"),
    PROTIDIN("protidin", "Protidin"),
    UNKNOWN("unknown", "Unknown Source");

    private final String code;
    private final String displayName;

    NewsSource(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static NewsSource fromCode(String code) {
        if (code == null) return UNKNOWN;
        
        for (NewsSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) {
                return source;
            }
        }
        return UNKNOWN;
    }

    public static NewsSource fromDisplayName(String displayName) {
        if (displayName == null) return UNKNOWN;
        
        for (NewsSource source : values()) {
            if (source.displayName.equalsIgnoreCase(displayName)) {
                return source;
            }
        }
        return UNKNOWN;
    }
}