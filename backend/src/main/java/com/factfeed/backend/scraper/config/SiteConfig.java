package com.factfeed.backend.scraper.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteConfig {
    private String siteName;
    private String baseUrl;
    private boolean active;
}