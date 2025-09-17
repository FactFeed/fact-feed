package com.factfeed.backend.config;

import java.util.List;
import lombok.Data;
import org.springframework.stereotype.Component;

@Component
@Data
public class NewsSourcesProperties {
    private List<NewsSourceConfig> sources;
}