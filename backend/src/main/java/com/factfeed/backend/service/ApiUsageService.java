package com.factfeed.backend.service;

import com.factfeed.backend.model.entity.ApiUsageLog;
import com.factfeed.backend.model.repository.ApiUsageLogRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for monitoring and managing API usage with rate limiting
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiUsageService {

    private final ApiUsageLogRepository apiUsageLogRepository;
    // In-memory cache for current usage statistics
    private final Map<String, UsageStats> currentUsageCache = new ConcurrentHashMap<>();
    @Value("${api.rate-limit.requests-per-minutes:10}")
    private int maxRequestsPerMinute;
    @Value("${api.rate-limit.tokens-per-minutes:250000}")
    private int maxTokensPerMinute;

    /**
     * Check if API request is allowed based on rate limits
     */
    public boolean isRequestAllowed(String provider, String accountKey) {
        try {
            String cacheKey = provider + ":" + accountKey;
            UsageStats stats = getCurrentUsageStats(provider, accountKey);

            // Update cache
            currentUsageCache.put(cacheKey, stats);

            // Check rate limits
            boolean requestAllowed = stats.requestCount < maxRequestsPerMinute;
            boolean tokenAllowed = stats.tokenCount < maxTokensPerMinute;

            if (!requestAllowed) {
                log.warn("Request rate limit exceeded for {}:{} - {} requests in last minute",
                        provider, accountKey, stats.requestCount);
            }

            if (!tokenAllowed) {
                log.warn("Token rate limit exceeded for {}:{} - {} tokens in last minute",
                        provider, accountKey, stats.tokenCount);
            }

            return requestAllowed && tokenAllowed;

        } catch (Exception e) {
            log.error("Error checking rate limits for {}:{}: {}", provider, accountKey, e.getMessage());
            // Allow request if we can't check limits (fail open)
            return true;
        }
    }

    /**
     * Log API usage
     */
    public void logApiUsage(String provider, String accountKey, String requestType,
                            String modelName, int inputTokens, int outputTokens,
                            long responseTimeMs, boolean success, String errorMessage) {
        try {
            ApiUsageLog log = new ApiUsageLog();
            log.setApiProvider(provider);
            log.setAccountKey(accountKey);
            log.setRequestType(requestType);
            log.setModelName(modelName);
            log.setInputTokens(inputTokens);
            log.setOutputTokens(outputTokens);
            log.setTotalTokens(inputTokens + outputTokens);
            log.setResponseTimeMs(responseTimeMs);
            log.setSuccess(success);
            log.setErrorMessage(errorMessage);
            log.setCreatedAt(LocalDateTime.now());

            // Estimate cost (simplified - adjust based on actual pricing)
            log.setRequestCost(estimateCost(provider, modelName, inputTokens, outputTokens));

            apiUsageLogRepository.save(log);

            // Update cache
            String cacheKey = provider + ":" + accountKey;
            UsageStats stats = currentUsageCache.getOrDefault(cacheKey, new UsageStats());
            stats.requestCount++;
            stats.tokenCount += log.getTotalTokens();
            stats.totalCost += log.getRequestCost();
            currentUsageCache.put(cacheKey, stats);

        } catch (Exception e) {
            log.error("Error logging API usage: {}", e.getMessage());
        }
    }

    /**
     * Get current usage statistics for a provider and account
     */
    public UsageStats getCurrentUsageStats(String provider, String accountKey) {
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);

        Long requestCount = apiUsageLogRepository.countRequestsByProviderAndAccountSince(
                provider, accountKey, oneMinuteAgo);
        Long tokenCount = apiUsageLogRepository.sumTokensByProviderAndAccountSince(
                provider, accountKey, oneMinuteAgo);
        Double totalCost = apiUsageLogRepository.sumCostByProviderAndAccountSince(
                provider, accountKey, oneMinuteAgo);

        UsageStats stats = new UsageStats();
        stats.requestCount = requestCount != null ? requestCount.intValue() : 0;
        stats.tokenCount = tokenCount != null ? tokenCount.intValue() : 0;
        stats.totalCost = totalCost != null ? totalCost : 0.0;

        return stats;
    }

    /**
     * Get usage statistics by provider
     */
    public Map<String, UsageStats> getUsageStatsByProvider(int hours) {
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        List<Object[]> providerStats = apiUsageLogRepository.getUsageStatsByProvider(fromDate);

        Map<String, UsageStats> statsMap = new HashMap<>();

        for (Object[] row : providerStats) {
            String provider = (String) row[0];
            Long requests = (Long) row[1];
            Long tokens = (Long) row[2];
            Double cost = (Double) row[3];

            UsageStats stats = new UsageStats();
            stats.requestCount = requests != null ? requests.intValue() : 0;
            stats.tokenCount = tokens != null ? tokens.intValue() : 0;
            stats.totalCost = cost != null ? cost : 0.0;

            statsMap.put(provider, stats);
        }

        return statsMap;
    }

    /**
     * Get usage statistics by request type
     */
    public Map<String, RequestTypeStats> getUsageStatsByRequestType(int hours) {
        LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);
        List<Object[]> requestTypeStats = apiUsageLogRepository.getUsageStatsByRequestType(fromDate);

        Map<String, RequestTypeStats> statsMap = new HashMap<>();

        for (Object[] row : requestTypeStats) {
            String requestType = (String) row[0];
            Long requests = (Long) row[1];
            Double avgTokens = (Double) row[2];
            Double avgResponseTime = (Double) row[3];

            RequestTypeStats stats = new RequestTypeStats();
            stats.requestCount = requests != null ? requests.intValue() : 0;
            stats.averageTokens = avgTokens != null ? avgTokens : 0.0;
            stats.averageResponseTimeMs = avgResponseTime != null ? avgResponseTime : 0.0;

            statsMap.put(requestType, stats);
        }

        return statsMap;
    }

    /**
     * Check if we need to switch to a different API account
     */
    public boolean shouldSwitchAccount(String provider, String accountKey) {
        UsageStats stats = getCurrentUsageStats(provider, accountKey);

        // Switch if approaching limits (80% threshold)
        boolean approachingRequestLimit = stats.requestCount > (maxRequestsPerMinute * 0.8);
        boolean approachingTokenLimit = stats.tokenCount > (maxTokensPerMinute * 0.8);

        return approachingRequestLimit || approachingTokenLimit;
    }

    /**
     * Get available API accounts for a provider (placeholder implementation)
     */
    public List<String> getAvailableAccounts(String provider) {
        // This is a placeholder - implement based on your configuration
        // You might store multiple API keys in configuration or database
        return List.of("primary_account", "backup_account_1", "backup_account_2");
    }

    /**
     * Select best available account for a provider
     */
    public String selectBestAccount(String provider) {
        List<String> accounts = getAvailableAccounts(provider);

        // Find account with lowest current usage
        String bestAccount = null;
        int lowestUsage = Integer.MAX_VALUE;

        for (String account : accounts) {
            if (isRequestAllowed(provider, account)) {
                UsageStats stats = getCurrentUsageStats(provider, account);
                int currentUsage = stats.requestCount + (stats.tokenCount / 100); // Weighted score

                if (currentUsage < lowestUsage) {
                    lowestUsage = currentUsage;
                    bestAccount = account;
                }
            }
        }

        return bestAccount != null ? bestAccount : accounts.get(0); // Fallback to first account
    }

    /**
     * Estimate API cost (simplified - adjust based on actual pricing)
     */
    private Double estimateCost(String provider, String modelName, int inputTokens, int outputTokens) {
        // Simplified cost calculation for Gemini
        if ("gemini".equalsIgnoreCase(provider)) {
            // Approximate Gemini pricing (adjust based on actual rates)
            double inputCostPer1k = 0.000125; // $0.000125 per 1K input tokens
            double outputCostPer1k = 0.000375; // $0.000375 per 1K output tokens

            double inputCost = (inputTokens / 1000.0) * inputCostPer1k;
            double outputCost = (outputTokens / 1000.0) * outputCostPer1k;

            return inputCost + outputCost;
        }

        return 0.0; // Unknown provider
    }

    /**
     * Clear old usage logs (cleanup)
     */
    public void cleanupOldUsageLogs(int daysToKeep) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
            // Note: You'll need to add this method to the repository
            // apiUsageLogRepository.deleteByCreatedAtBefore(cutoffDate);
            log.info("Cleanup of usage logs older than {} days completed", daysToKeep);
        } catch (Exception e) {
            log.error("Error cleaning up old usage logs: {}", e.getMessage());
        }
    }

    /**
     * Usage statistics data class
     */
    public static class UsageStats {
        public int requestCount = 0;
        public int tokenCount = 0;
        public double totalCost = 0.0;

        // Getters and setters
        public int getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(int requestCount) {
            this.requestCount = requestCount;
        }

        public int getTokenCount() {
            return tokenCount;
        }

        public void setTokenCount(int tokenCount) {
            this.tokenCount = tokenCount;
        }

        public double getTotalCost() {
            return totalCost;
        }

        public void setTotalCost(double totalCost) {
            this.totalCost = totalCost;
        }
    }

    /**
     * Request type statistics data class
     */
    public static class RequestTypeStats {
        public int requestCount = 0;
        public double averageTokens = 0.0;
        public double averageResponseTimeMs = 0.0;

        // Getters and setters
        public int getRequestCount() {
            return requestCount;
        }

        public void setRequestCount(int requestCount) {
            this.requestCount = requestCount;
        }

        public double getAverageTokens() {
            return averageTokens;
        }

        public void setAverageTokens(double averageTokens) {
            this.averageTokens = averageTokens;
        }

        public double getAverageResponseTimeMs() {
            return averageResponseTimeMs;
        }

        public void setAverageResponseTimeMs(double averageResponseTimeMs) {
            this.averageResponseTimeMs = averageResponseTimeMs;
        }
    }
}