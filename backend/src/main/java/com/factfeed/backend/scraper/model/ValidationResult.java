package com.factfeed.backend.scraper.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;
    private List<String> issues;

    /**
     * Create a successful validation result
     */
    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    /**
     * Create a failed validation result with issues
     */
    public static ValidationResult failure(List<String> issues) {
        return new ValidationResult(false, issues);
    }

    /**
     * Create a failed validation result with single issue
     */
    public static ValidationResult failure(String issue) {
        return new ValidationResult(false, List.of(issue));
    }

    /**
     * Check if validation has any issues
     */
    public boolean hasIssues() {
        return issues != null && !issues.isEmpty();
    }

    /**
     * Get formatted error message
     */
    public String getErrorMessage() {
        if (!hasIssues()) return "";
        return String.join("; ", issues);
    }
}