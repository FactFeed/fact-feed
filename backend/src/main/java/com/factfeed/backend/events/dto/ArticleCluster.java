package com.factfeed.backend.events.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleCluster {
    private String eventTitle;
    private String eventType;
    private Double confidenceScore;
    private List<Long> articleIds;
    private String reasoning; // AI explanation for why these articles were grouped together
}