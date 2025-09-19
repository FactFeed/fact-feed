package com.factfeed.backend.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Entity to track API usage for monitoring rate limits and token consumption
 */
@Entity
@Table(name = "api_usage_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiUsageLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_provider", nullable = false, length = 50)
    private String apiProvider; // "gemini", "openai", etc.

    @Column(name = "account_key", nullable = false, length = 100)
    private String accountKey; // Hash or identifier of the API key used

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType; // "summarization", "event_mapping", "aggregation", etc.

    @Column(name = "model_name", length = 100)
    private String modelName; // "gemini-2.5-flash", etc.

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "request_cost")
    private Double requestCost; // Estimated cost

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "success")
    private Boolean success;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "request_metadata", columnDefinition = "TEXT")
    private String requestMetadata; // JSON metadata about the request

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Helper method to check if this is a recent usage
     */
    public boolean isWithinWindow(int hours) {
        return createdAt != null &&
                createdAt.isAfter(LocalDateTime.now().minusHours(hours));
    }
}