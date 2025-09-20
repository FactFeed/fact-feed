package com.factfeed.backend.ai.repository;

import com.factfeed.backend.ai.entity.ApiUsageLog;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiUsageLogRepository extends JpaRepository<ApiUsageLog, Long> {

    @Query("SELECT COUNT(a) FROM ApiUsageLog a WHERE a.apiKeyName = :keyName AND a.usedAt >= :since")
    Long countUsageByKeyNameSince(@Param("keyName") String keyName, @Param("since") LocalDateTime since);

    @Query("SELECT SUM(a.tokenCount) FROM ApiUsageLog a WHERE a.apiKeyName = :keyName AND a.usedAt >= :since")
    Long sumTokensByKeyNameSince(@Param("keyName") String keyName, @Param("since") LocalDateTime since);

    List<ApiUsageLog> findByArticleId(Long articleId);

    @Query("SELECT a FROM ApiUsageLog a WHERE a.success = false ORDER BY a.usedAt DESC")
    List<ApiUsageLog> findFailedOperations();
}