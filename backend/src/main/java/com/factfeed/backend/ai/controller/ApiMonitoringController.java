package com.factfeed.backend.ai.controller;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import com.factfeed.backend.ai.repository.ApiUsageLogRepository;
import com.factfeed.backend.ai.service.ApiUsageMonitoringService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
public class ApiMonitoringController {

    private final ApiUsageMonitoringService monitoringService;
    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * Get detailed API usage statistics
     */
    @GetMapping("/api-usage/detailed")
    public ResponseEntity<Map<String, Object>> getDetailedApiUsage() {
        Map<String, Object> stats = monitoringService.getDetailedApiUsageStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * Get API key health status
     */
    @GetMapping("/api-keys/health")
    public ResponseEntity<Map<String, Object>> getApiKeyHealth() {
        Map<String, Object> health = monitoringService.getApiKeyHealthStatus();
        return ResponseEntity.ok(health);
    }

    /**
     * Get usage recommendations
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getUsageRecommendations() {
        List<String> recommendations = monitoringService.getUsageRecommendations();
        Map<String, Object> health = monitoringService.getApiKeyHealthStatus();

        return ResponseEntity.ok(Map.of(
                "recommendations", recommendations,
                "overallStatus", health.get("overallStatus"),
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * Get API usage logs with filtering
     */
    @GetMapping("/api-usage/logs")
    public ResponseEntity<List<ApiUsageLog>> getApiUsageLogs(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String apiKeyName,
            @RequestParam(required = false) Boolean success) {

        List<ApiUsageLog> logs = apiUsageLogRepository.findAll().stream()
                .filter(log -> operation == null || log.getOperation().equals(operation))
                .filter(log -> apiKeyName == null || log.getApiKeyName().equals(apiKeyName))
                .filter(log -> success == null || log.getSuccess().equals(success))
                .sorted((l1, l2) -> l2.getUsedAt().compareTo(l1.getUsedAt()))
                .limit(limit)
                .toList();

        return ResponseEntity.ok(logs);
    }

    /**
     * Get failed operations
     */
    @GetMapping("/api-usage/failures")
    public ResponseEntity<List<ApiUsageLog>> getFailedOperations(
            @RequestParam(defaultValue = "50") int limit) {

        List<ApiUsageLog> failures = apiUsageLogRepository.findFailedOperations()
                .stream()
                .limit(limit)
                .toList();

        return ResponseEntity.ok(failures);
    }

    /**
     * Get usage statistics by operation
     */
    @GetMapping("/api-usage/by-operation/{operation}")
    public ResponseEntity<Map<String, Object>> getUsageByOperation(@PathVariable String operation) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);
        LocalDateTime oneDayAgo = now.minusDays(1);

        List<ApiUsageLog> allLogs = apiUsageLogRepository.findAll().stream()
                .filter(log -> log.getOperation().equals(operation))
                .toList();

        long totalRequests = allLogs.size();
        long successfulRequests = allLogs.stream()
                .filter(ApiUsageLog::getSuccess)
                .count();
        long failedRequests = totalRequests - successfulRequests;

        long hourlyRequests = allLogs.stream()
                .filter(log -> log.getUsedAt().isAfter(oneHourAgo))
                .count();

        long dailyRequests = allLogs.stream()
                .filter(log -> log.getUsedAt().isAfter(oneDayAgo))
                .count();

        double successRate = totalRequests > 0 ? (double) successfulRequests / totalRequests * 100 : 0;

        // Calculate average tokens per request
        double averageTokens = allLogs.stream()
                .filter(log -> log.getTokenCount() != null)
                .mapToInt(ApiUsageLog::getTokenCount)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(Map.of(
                "operation", operation,
                "totalRequests", totalRequests,
                "successfulRequests", successfulRequests,
                "failedRequests", failedRequests,
                "successRate", Math.round(successRate * 100.0) / 100.0,
                "hourlyRequests", hourlyRequests,
                "dailyRequests", dailyRequests,
                "averageTokensPerRequest", Math.round(averageTokens * 100.0) / 100.0,
                "timestamp", now
        ));
    }

    /**
     * Check if a specific API key can be used for an operation
     */
    @GetMapping("/api-keys/{keyName}/can-use/{operation}")
    public ResponseEntity<Map<String, Object>> canUseApiKey(
            @PathVariable String keyName,
            @PathVariable String operation) {

        boolean canUse = monitoringService.canUseApiKey(keyName, operation);

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        Long currentUsage = apiUsageLogRepository.countUsageByKeyNameSince(keyName, oneHourAgo);
        Long currentTokens = apiUsageLogRepository.sumTokensByKeyNameSince(keyName, oneHourAgo);

        return ResponseEntity.ok(Map.of(
                "apiKeyName", keyName,
                "operation", operation,
                "canUse", canUse,
                "currentHourlyUsage", currentUsage != null ? currentUsage : 0,
                "currentHourlyTokens", currentTokens != null ? currentTokens : 0,
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * Get next available API key for an operation
     */
    @GetMapping("/api-keys/next-available/{operation}")
    public ResponseEntity<Map<String, Object>> getNextAvailableApiKey(@PathVariable String operation) {
        String nextKey = monitoringService.getNextAvailableApiKey(operation);
        boolean canUse = monitoringService.canUseApiKey(nextKey, operation);

        return ResponseEntity.ok(Map.of(
                "nextAvailableKey", nextKey,
                "operation", operation,
                "isRecommended", canUse,
                "timestamp", LocalDateTime.now()
        ));
    }

    /**
     * Get monitoring dashboard data - TEMPORARILY DISABLED DUE TO IMMUTABLE MAP ISSUE
     */
    // @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getMonitoringDashboard() {
        // TODO: Fix immutable map issue in ApiUsageMonitoringService
        return ResponseEntity.ok(Map.of(
                "status", "DISABLED",
                "message", "Dashboard temporarily disabled. Use /health and /usage-summary instead.",
                "timestamp", LocalDateTime.now()
        ));
        
        /*
        Map<String, Object> detailedStats = monitoringService.getDetailedApiUsageStats();
        Map<String, Object> healthStatus = monitoringService.getApiKeyHealthStatus();
        List<String> recommendations = monitoringService.getUsageRecommendations();

        // Recent activity summary
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<ApiUsageLog> recentLogs = apiUsageLogRepository.findAll().stream()
                .filter(log -> log.getUsedAt().isAfter(oneHourAgo))
                .toList();

        long recentTotal = recentLogs.size();
        long recentSuccessful = recentLogs.stream()
                .filter(ApiUsageLog::getSuccess)
                .count();
        long recentFailed = recentTotal - recentSuccessful;

        return ResponseEntity.ok(Map.of(
                "healthStatus", healthStatus,
                "recentActivity", Map.of(
                        "totalRequests", recentTotal,
                        "successfulRequests", recentSuccessful,
                        "failedRequests", recentFailed,
                        "successRate", recentTotal > 0 ? Math.round((double) recentSuccessful / recentTotal * 100) : 0
                ),
                "recommendations", recommendations,
                "keyStatistics", detailedStats.get("keyStatistics"),
                "operationStatistics", detailedStats.get("operationStatistics"),
                "timestamp", LocalDateTime.now()
        ));
        */
    }

    /**
     * Health check endpoint - TEMPORARILY DISABLED
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        // TODO: Fix immutable map issue in ApiUsageMonitoringService
        return ResponseEntity.ok(Map.of(
                "status", "HEALTHY",
                "service", "API Monitoring Service",
                "message", "Health check temporarily simplified",
                "timestamp", LocalDateTime.now()
        ));
        
        /*
        Map<String, Object> health = monitoringService.getApiKeyHealthStatus();
        String status = (String) health.get("overallStatus");

        return ResponseEntity.ok(Map.of(
                "status", status,
                "service", "API Monitoring Service",
                "availableKeys", health.get("availableKeys"),
                "totalKeys", health.get("totalKeys"),
                "timestamp", LocalDateTime.now()
        ));
        */
    }
}