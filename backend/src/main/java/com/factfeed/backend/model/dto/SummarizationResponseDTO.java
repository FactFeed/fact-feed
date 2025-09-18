package com.factfeed.backend.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for AI summarization response
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummarizationResponseDTO {
    private Long articleId;
    private String originalTitle;
    private String summary;
    private boolean success;
    private String error;

    public static SummarizationResponseDTO success(Long articleId, String originalTitle, String summary) {
        return new SummarizationResponseDTO(articleId, originalTitle, summary, true, null);
    }

    public static SummarizationResponseDTO failure(Long articleId, String originalTitle, String error) {
        return new SummarizationResponseDTO(articleId, originalTitle, null, false, error);
    }
}