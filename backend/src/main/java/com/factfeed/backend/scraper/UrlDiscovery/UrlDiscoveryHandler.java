package com.factfeed.backend.scraper.UrlDiscovery;

import java.util.List;

public interface UrlDiscoveryHandler {
    List<String> discoveredUrls(int max);
}