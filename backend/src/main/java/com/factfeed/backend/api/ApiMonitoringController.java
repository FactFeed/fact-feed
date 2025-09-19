package com.factfeed.backend.api;

import com.factfeed.backend.model.repository.ApiUsageLogRepository;
import com.factfeed.backend.service.ApiUsageService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for API usage monitoring and statistics
 */
@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Slf4j
public class ApiMonitoringController {

    private final ApiUsageService apiUsageService;
    private final ApiUsageLogRepository apiUsageLogRepository;

    /**
     * Get current usage statistics
     */
    @GetMapping("/usage/current")
    public ResponseEntity<Map<String, Object>> getCurrentUsage(
            @RequestParam(defaultValue = "gemini") String provider,
            @RequestParam(required = false) String accountKey) {

        try {
            Map<String, Object> response = new HashMap<>();

            if (accountKey != null) {
                // Get stats for specific account
                ApiUsageService.UsageStats stats = apiUsageService.getCurrentUsageStats(provider, accountKey);
                response.put("provider", provider);
                response.put("account", accountKey);
                response.put("requests_last_hour", stats.getRequestCount());
                response.put("tokens_last_hour", stats.getTokenCount());
                response.put("cost_last_hour", stats.getTotalCost());
                response.put("rate_limit_status", apiUsageService.isRequestAllowed(provider, accountKey) ? "OK" : "EXCEEDED");
            } else {
                // Get stats for all accounts of the provider
                Map<String, ApiUsageService.UsageStats> providerStats = apiUsageService.getUsageStatsByProvider(1);
                response.put("provider", provider);
                response.put("stats_by_provider", providerStats);
            }

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting current usage: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get usage statistics by time period
     */
    @GetMapping("/usage/stats")
    public ResponseEntity<Map<String, Object>> getUsageStats(
            @RequestParam(defaultValue = "24") int hours) {

        try {
            Map<String, Object> response = new HashMap<>();

            // Get stats by provider
            Map<String, ApiUsageService.UsageStats> providerStats = apiUsageService.getUsageStatsByProvider(hours);
            response.put("by_provider", providerStats);

            // Get stats by request type
            Map<String, ApiUsageService.RequestTypeStats> requestTypeStats = apiUsageService.getUsageStatsByRequestType(hours);
            response.put("by_request_type", requestTypeStats);

            // Get recent failed requests
            var failedRequests = apiUsageLogRepository.findFailedRequests(PageRequest.of(0, 10));
            response.put("recent_failures", failedRequests.getContent());

            response.put("time_period_hours", hours);
            response.put("generated_at", LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting usage stats: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get performance statistics
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceStats(
            @RequestParam(defaultValue = "24") int hours) {

        try {
            Map<String, Object> response = new HashMap<>();
            LocalDateTime fromDate = LocalDateTime.now().minusHours(hours);

            // Get average response times by provider
            var responseTimeStats = apiUsageLogRepository.getAverageResponseTimeByProvider(fromDate);
            Map<String, Double> responseTimeMap = new HashMap<>();
            for (Object[] row : responseTimeStats) {
                responseTimeMap.put((String) row[0], (Double) row[1]);
            }
            response.put("avg_response_time_by_provider", responseTimeMap);

            // Get slow requests
            var slowRequests = apiUsageLogRepository.findSlowRequests(5000L, PageRequest.of(0, 10));
            response.put("slow_requests", slowRequests.getContent());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting performance stats: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Check rate limit status
     */
    @GetMapping("/rate-limit/check")
    public ResponseEntity<Map<String, Object>> checkRateLimit(
            @RequestParam(defaultValue = "gemini") String provider,
            @RequestParam String accountKey) {

        try {
            Map<String, Object> response = new HashMap<>();

            boolean allowed = apiUsageService.isRequestAllowed(provider, accountKey);
            ApiUsageService.UsageStats stats = apiUsageService.getCurrentUsageStats(provider, accountKey);

            response.put("provider", provider);
            response.put("account", accountKey);
            response.put("rate_limit_ok", allowed);
            response.put("current_requests", stats.getRequestCount());
            response.put("current_tokens", stats.getTokenCount());
            response.put("should_switch_account", apiUsageService.shouldSwitchAccount(provider, accountKey));
            response.put("recommended_account", apiUsageService.selectBestAccount(provider));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error checking rate limit: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get available accounts for load balancing
     */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> getAvailableAccounts(
            @RequestParam(defaultValue = "gemini") String provider) {

        try {
            Map<String, Object> response = new HashMap<>();

            var accounts = apiUsageService.getAvailableAccounts(provider);
            response.put("provider", provider);
            response.put("available_accounts", accounts);
            response.put("recommended_account", apiUsageService.selectBestAccount(provider));

            // Get status for each account
            Map<String, Object> accountStatuses = new HashMap<>();
            for (String account : accounts) {
                Map<String, Object> accountInfo = new HashMap<>();
                accountInfo.put("rate_limit_ok", apiUsageService.isRequestAllowed(provider, account));
                accountInfo.put("usage", apiUsageService.getCurrentUsageStats(provider, account));
                accountStatuses.put(account, accountInfo);
            }
            response.put("account_statuses", accountStatuses);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting available accounts: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get overall health status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getMonitoringHealth() {
        try {
            Map<String, Object> health = new HashMap<>();

            // Check if we can access usage data
            apiUsageService.getUsageStatsByProvider(1);
            health.put("usage_tracking", "healthy");

            // Check database connectivity
            apiUsageLogRepository.count();
            health.put("database", "healthy");

            health.put("status", "healthy");
            health.put("timestamp", LocalDateTime.now());

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Monitoring health check failed: {}", e.getMessage());

            Map<String, Object> health = new HashMap<>();
            health.put("status", "unhealthy");
            health.put("error", e.getMessage());
            health.put("timestamp", LocalDateTime.now());

            return ResponseEntity.status(503).body(health);
        }
    }
}