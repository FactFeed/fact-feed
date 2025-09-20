package com.factfeed.backend.events.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventMergeCandidate {
    private List<Long> eventIds;
    private String mergedTitle;
    private String mergedEventType;
    private Double confidenceScore;
    private String reasoning; // AI explanation for why these events should be merged
}