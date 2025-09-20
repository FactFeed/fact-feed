package com.factfeed.backend.ai.service;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiUsageMonitoringService {

    // Rate limits (per hour per key)
    private static final Long SUMMARIZATION_RATE_LIMIT = 1000L;
    private static final Long EVENT_MAPPING_RATE_LIMIT = 500L;
    private static final Long AGGREGATION_RATE_LIMIT = 300L;
    // Token limits (per hour per key)
    private static final Long SUMMARIZATION_TOKEN_LIMIT = 50000L;
    private static final Long EVENT_MAPPING_TOKEN_LIMIT = 100000L;
    private static final Long AGGREGATION_TOKEN_LIMIT = 150000L;
    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * Check if an API key can be used for a specific operation
     */
    public boolean canUseApiKey(String apiKeyName, String operation) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        Long currentUsage = apiUsageLogRepository.countUsageByKeyNameSince(apiKeyName, oneHourAgo);
        Long currentTokens = apiUsageLogRepository.sumTokensByKeyNameSince(apiKeyName, oneHourAgo);

        if (currentUsage == null) currentUsage = 0L;
        if (currentTokens == null) currentTokens = 0L;

        // Check rate limits based on operation
        Long rateLimit = getRateLimit(operation);
        Long tokenLimit = getTokenLimit(operation);

        boolean withinRateLimit = currentUsage < rateLimit;
        boolean withinTokenLimit = currentTokens < tokenLimit;

        if (!withinRateLimit) {
            log.warn("⚠️ Rate limit exceeded for key {}: {}/{} requests", apiKeyName, currentUsage, rateLimit);
        }

        if (!withinTokenLimit) {
            log.warn("⚠️ Token limit exceeded for key {}: {}/{} tokens", apiKeyName, currentTokens, tokenLimit);
        }

        return withinRateLimit && withinTokenLimit;
    }

    /**
     * Get the next available API key for an operation
     */
    public String getNextAvailableApiKey(String operation) {
        String[] keys = {
                "GEMINI_KEY_Z1", "GEMINI_KEY_Z2", "GEMINI_KEY_Z3",
                "GEMINI_KEY_Z4", "GEMINI_KEY_C5", "GEMINI_KEY_C1"
        };

        // Try each key in order
        for (String key : keys) {
            if (canUseApiKey(key, operation)) {
                log.debug("✅ Using available API key: {} for operation: {}", key, operation);
                return key;
            }
        }

        // If no key is available, return the first one (with warning)
        log.warn("⚠️ No API keys available for operation: {}, using first key with rate limit risk", operation);
        return keys[0];
    }

    /**
     * Get comprehensive API usage statistics
     */
    public Map<String, Object> getDetailedApiUsageStats() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime oneDayAgo = now.minusDays(1);
        LocalDateTime oneWeekAgo = now.minusDays(7);

        String[] keys = {
                "GEMINI_KEY_Z1", "GEMINI_KEY_Z2", "GEMINI_KEY_Z3",
                "GEMINI_KEY_Z4", "GEMINI_KEY_C5", "GEMINI_KEY_C1"
        };

        // Per-key statistics
        Map<String, Map<String, Object>> keyStats = new HashMap<>();

        for (String key : keys) {
            Long hourlyUsage = apiUsageLogRepository.countUsageByKeyNameSince(key, oneHourAgo);
            Long hourlyTokens = apiUsageLogRepository.sumTokensByKeyNameSince(key, oneHourAgo);
            Long dailyUsage = apiUsageLogRepository.countUsageByKeyNameSince(key, oneDayAgo);
            Long dailyTokens = apiUsageLogRepository.sumTokensByKeyNameSince(key, oneDayAgo);

            keyStats.put(key, Map.of(
                    "hourlyUsage", hourlyUsage != null ? hourlyUsage : 0,
                    "hourlyTokens", hourlyTokens != null ? hourlyTokens : 0,
                    "dailyUsage", dailyUsage != null ? dailyUsage : 0,
                    "dailyTokens", dailyTokens != null ? dailyTokens : 0,
                    "available", canUseApiKey(key, "SUMMARIZE")
            ));
        }

        // Operation-wise statistics
        Map<String, Long> operationStats = Map.of(
                "SUMMARIZE", getOperationCount("SUMMARIZE", oneDayAgo),
                "EVENT_MAPPING", getOperationCount("EVENT_MAPPING", oneDayAgo),
                "AGGREGATION", getOperationCount("AGGREGATION", oneDayAgo)
        );

        // Recent failures
        List<ApiUsageLog> recentFailures = apiUsageLogRepository.findFailedOperations()
                .stream()
                .filter(log -> log.getUsedAt().isAfter(oneDayAgo))
                .limit(10)
                .toList();

        return Map.of(
                "keyStatistics", keyStats,
                "operationStatistics", operationStats,
                "recentFailures", recentFailures,
                "rateLimits", Map.of(
                        "SUMMARIZE", SUMMARIZATION_RATE_LIMIT,
                        "EVENT_MAPPING", EVENT_MAPPING_RATE_LIMIT,
                        "AGGREGATION", AGGREGATION_RATE_LIMIT
                ),
                "tokenLimits", Map.of(
                        "SUMMARIZE", SUMMARIZATION_TOKEN_LIMIT,
                        "EVENT_MAPPING", EVENT_MAPPING_TOKEN_LIMIT,
                        "AGGREGATION", AGGREGATION_TOKEN_LIMIT
                ),
                "timestamp", now
        );
    }

    /**
     * Get health status of all API keys
     */
    public Map<String, Object> getApiKeyHealthStatus() {
        String[] keys = {
                "GEMINI_KEY_Z1", "GEMINI_KEY_Z2", "GEMINI_KEY_Z3",
                "GEMINI_KEY_Z4", "GEMINI_KEY_C5", "GEMINI_KEY_C1"
        };

        Map<String, Boolean> keyHealth = new HashMap<>();
        int availableKeys = 0;

        for (String key : keys) {
            boolean isHealthy = canUseApiKey(key, "SUMMARIZE") &&
                    canUseApiKey(key, "EVENT_MAPPING") &&
                    canUseApiKey(key, "AGGREGATION");
            keyHealth.put(key, isHealthy);
            if (isHealthy) availableKeys++;
        }

        String overallStatus = availableKeys > 3 ? "HEALTHY" :
                availableKeys > 1 ? "WARNING" : "CRITICAL";

        return Map.of(
                "overallStatus", overallStatus,
                "availableKeys", availableKeys,
                "totalKeys", keys.length,
                "keyHealth", keyHealth,
                "healthPercentage", Math.round((double) availableKeys / keys.length * 100),
                "timestamp", LocalDateTime.now()
        );
    }

    /**
     * Log critical usage patterns
     */
    public void logCriticalUsage(String apiKeyName, String operation, String message) {
        log.warn("🚨 CRITICAL API USAGE - Key: {}, Operation: {}, Message: {}",
                apiKeyName, operation, message);

        // Here you could implement additional alerting mechanisms:
        // - Email notifications
        // - Slack/Discord webhooks
        // - Database alerts table
        // - External monitoring systems
    }

    /**
     * Get usage recommendations
     */
    public List<String> getUsageRecommendations() {
        List<String> recommendations = List.of();
        Map<String, Object> healthStatus = getApiKeyHealthStatus();

        int availableKeys = (Integer) healthStatus.get("availableKeys");
        String overallStatus = (String) healthStatus.get("overallStatus");

        if ("CRITICAL".equals(overallStatus)) {
            recommendations.add("🚨 Critical: Only " + availableKeys + " API keys available. Consider waiting before making new requests.");
            recommendations.add("💡 Reduce batch sizes to conserve API quota");
            recommendations.add("⏰ Schedule non-urgent operations for off-peak hours");
        } else if ("WARNING".equals(overallStatus)) {
            recommendations.add("⚠️ Warning: Limited API keys available. Monitor usage closely.");
            recommendations.add("📊 Consider implementing request queuing");
        } else {
            recommendations.add("✅ All systems operating normally");
            recommendations.add("🚀 Safe to proceed with batch operations");
        }

        return recommendations;
    }

    // Private helper methods

    private Long getRateLimit(String operation) {
        return switch (operation) {
            case "SUMMARIZE" -> SUMMARIZATION_RATE_LIMIT;
            case "EVENT_MAPPING" -> EVENT_MAPPING_RATE_LIMIT;
            case "AGGREGATION" -> AGGREGATION_RATE_LIMIT;
            default -> SUMMARIZATION_RATE_LIMIT;
        };
    }

    private Long getTokenLimit(String operation) {
        return switch (operation) {
            case "SUMMARIZE" -> SUMMARIZATION_TOKEN_LIMIT;
            case "EVENT_MAPPING" -> EVENT_MAPPING_TOKEN_LIMIT;
            case "AGGREGATION" -> AGGREGATION_TOKEN_LIMIT;
            default -> SUMMARIZATION_TOKEN_LIMIT;
        };
    }

    private Long getOperationCount(String operation, LocalDateTime since) {
        // This would need a custom query in the repository
        // For now, using a simplified approach
        return apiUsageLogRepository.findAll().stream()
                .filter(log -> log.getOperation().equals(operation))
                .filter(log -> log.getUsedAt().isAfter(since))
                .count();
    }
}