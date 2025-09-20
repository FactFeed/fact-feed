package com.factfeed.backend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummarizationResponse {
    private Long id;
    private String title;
    private String summarizedContent;
}