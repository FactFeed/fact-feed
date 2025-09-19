package com.factfeed.backend.model.repository;

import com.factfeed.backend.model.entity.DiscrepancyReport;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DiscrepancyReportRepository extends JpaRepository<DiscrepancyReport, Long> {

    /**
     * Find discrepancies by aggregated content ID
     */
    List<DiscrepancyReport> findByAggregatedContentId(Long aggregatedContentId);

    /**
     * Find discrepancies by type
     */
    Page<DiscrepancyReport> findByType(DiscrepancyReport.DiscrepancyType type, Pageable pageable);

    /**
     * Find high severity discrepancies
     */
    @Query("SELECT dr FROM DiscrepancyReport dr WHERE dr.severityScore >= :minSeverity ORDER BY dr.severityScore DESC")
    Page<DiscrepancyReport> findHighSeverityDiscrepancies(@Param("minSeverity") Double minSeverity, Pageable pageable);

    /**
     * Find recent discrepancies
     */
    @Query("SELECT dr FROM DiscrepancyReport dr WHERE dr.detectedAt >= :fromDate ORDER BY dr.detectedAt DESC")
    Page<DiscrepancyReport> findRecentDiscrepancies(@Param("fromDate") LocalDateTime fromDate, Pageable pageable);

    /**
     * Find discrepancies with high confidence
     */
    @Query("SELECT dr FROM DiscrepancyReport dr WHERE dr.confidenceScore >= :minConfidence ORDER BY dr.confidenceScore DESC")
    Page<DiscrepancyReport> findHighConfidenceDiscrepancies(@Param("minConfidence") Double minConfidence, Pageable pageable);

    /**
     * Search discrepancies by title or description
     */
    @Query("SELECT dr FROM DiscrepancyReport dr WHERE " +
            "LOWER(dr.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(dr.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<DiscrepancyReport> searchDiscrepancies(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Count discrepancies by type
     */
    @Query("SELECT dr.type, COUNT(dr) FROM DiscrepancyReport dr GROUP BY dr.type")
    List<Object[]> countDiscrepanciesByType();

    /**
     * Find discrepancies detected by specific model
     */
    Page<DiscrepancyReport> findByDetectedBy(String detectedBy, Pageable pageable);

    /**
     * Count discrepancies for an aggregated content
     */
    long countByAggregatedContentId(Long aggregatedContentId);

    /**
     * Delete all discrepancies associated with an aggregated content
     */
    void deleteByAggregatedContentId(Long aggregatedContentId);

    /**
     * Find critical discrepancies (high severity and high confidence)
     */
    @Query("SELECT dr FROM DiscrepancyReport dr WHERE " +
            "dr.severityScore >= :minSeverity AND dr.confidenceScore >= :minConfidence " +
            "ORDER BY dr.severityScore DESC, dr.confidenceScore DESC")
    Page<DiscrepancyReport> findCriticalDiscrepancies(@Param("minSeverity") Double minSeverity,
                                                      @Param("minConfidence") Double minConfidence,
                                                      Pageable pageable);
}