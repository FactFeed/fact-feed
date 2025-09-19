package com.factfeed.backend.model.repository;

import com.factfeed.backend.model.entity.ApiUsageLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    /**
     * Find usage logs by API provider
     */
    Page<ApiUsageLog> findByApiProvider(String apiProvider, Pageable pageable);

    /**
     * Find usage logs by account key
     */
    Page<ApiUsageLog> findByAccountKey(String accountKey, Pageable pageable);

    /**
     * Find usage logs within time window
     */
    @Query("SELECT aul FROM ApiUsageLog aul WHERE aul.createdAt >= :fromDate ORDER BY aul.createdAt DESC")
    Page<ApiUsageLog> findRecentUsage(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);

    /**
     * Count total tokens used by provider and account in time window
     */
    @Query("SELECT SUM(aul.totalTokens) FROM ApiUsageLog aul WHERE " +
            "aul.apiProvider = :provider AND aul.accountKey = :accountKey AND aul.createdAt >= :fromDate")
    Long sumTokensByProviderAndAccountSince(@Param("provider") String provider,
                                            @Param("accountKey") String accountKey,
                                            @Param("fromDate") LocalDateTime fromDate);

    /**
     * Count total requests by provider and account in time window
     */
    @Query("SELECT COUNT(aul) FROM ApiUsageLog aul WHERE " +
            "aul.apiProvider = :provider AND aul.accountKey = :accountKey AND aul.createdAt >= :fromDate")
    Long countRequestsByProviderAndAccountSince(@Param("provider") String provider,
                                                @Param("accountKey") String accountKey,
                                                @Param("fromDate") LocalDateTime fromDate);

    /**
     * Calculate total cost by provider and account in time window
     */
    @Query("SELECT SUM(aul.requestCost) FROM ApiUsageLog aul WHERE " +
            "aul.apiProvider = :provider AND aul.accountKey = :accountKey AND aul.createdAt >= :fromDate")
    Double sumCostByProviderAndAccountSince(@Param("provider") String provider,
                                            @Param("accountKey") String accountKey,
                                            @Param("fromDate") LocalDateTime fromDate);

    /**
     * Find failed requests
     */
    @Query("SELECT aul FROM ApiUsageLog aul WHERE aul.success = false ORDER BY aul.createdAt DESC")
    Page<ApiUsageLog> findFailedRequests(Pageable pageable);

    /**
     * Get usage statistics by request type
     */
    @Query("SELECT aul.requestType, COUNT(aul), AVG(aul.totalTokens), AVG(aul.responseTimeMs) " +
            "FROM ApiUsageLog aul WHERE aul.createdAt >= :fromDate GROUP BY aul.requestType")
    List<Object[]> getUsageStatsByRequestType(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Get usage statistics by provider
     */
    @Query("SELECT aul.apiProvider, COUNT(aul), SUM(aul.totalTokens), SUM(aul.requestCost) " +
            "FROM ApiUsageLog aul WHERE aul.createdAt >= :fromDate GROUP BY aul.apiProvider")
    List<Object[]> getUsageStatsByProvider(@Param("fromDate") LocalDateTime fromDate);

    /**
     * Find slow requests
     */
    @Query("SELECT aul FROM ApiUsageLog aul WHERE aul.responseTimeMs > :maxResponseTime ORDER BY aul.responseTimeMs DESC")
    Page<ApiUsageLog> findSlowRequests(@Param("maxResponseTime") Long maxResponseTime, Pageable pageable);

    /**
     * Get average response time by provider
     */
    @Query("SELECT aul.apiProvider, AVG(aul.responseTimeMs) FROM ApiUsageLog aul " +
            "WHERE aul.createdAt >= :fromDate GROUP BY aul.apiProvider")
    List<Object[]> getAverageResponseTimeByProvider(@Param("fromDate") LocalDateTime fromDate);
}