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
}