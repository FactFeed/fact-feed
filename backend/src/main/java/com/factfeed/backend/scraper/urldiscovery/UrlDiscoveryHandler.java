package com.factfeed.backend.scraper.urldiscovery;

import java.util.List;

public interface UrlDiscoveryHandler {
    List<String> discoveredUrls(int max);
}