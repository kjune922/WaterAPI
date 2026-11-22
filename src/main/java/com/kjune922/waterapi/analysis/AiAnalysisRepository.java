package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update AiAnalysis a
               set a.confirmedSummary = :summary, a.confirmedAbnormalityType = :abnormalityType,
                   a.confirmedRiskLevel = :riskLevel, a.confirmedRecommendedAction = :recommendedAction,
                   a.confirmedAt = :confirmedAt, a.reviewStatus = CONFIRMED
             where a.inspectionReport.id = :inspectionId and a.id = :analysisId
               and a.reviewStatus = PENDING_REVIEW
            """)
    int confirmPending(Long inspectionId, Long analysisId, String summary, String abnormalityType,
                       RiskLevel riskLevel, String recommendedAction, LocalDateTime confirmedAt);

    boolean existsByIdAndInspectionReportId(Long id, Long inspectionReportId);

    // 이미 분석된 점검일지 인지 체크
    boolean existsByInspectionReportId(Long inspectionReportId);

    // 특정 점검일지의 분석 결과 조회
    Optional<AiAnalysis> findByInspectionReportId(Long inspectionReportId);

    @EntityGraph(attributePaths = {"inspectionReport", "inspectionReport.facility"})
    @Query("""
            select a from AiAnalysis a
            where (:status is null or a.reviewStatus = :status)
              and (:risk is null or (a.reviewStatus = CONFIRMED and a.confirmedRiskLevel = :risk)
                   or (a.reviewStatus = PENDING_REVIEW and a.riskLevel = :risk))
            order by a.analyzedAt desc, a.id desc
            """)
    Page<AiAnalysis> search(RiskLevel risk, AnalysisReviewStatus status, Pageable pageable);

    // 점검일지 삭제 메소드 추가
    void deleteByInspectionReport_Id(Long inspectionId);

    // 위험도에따른 총집계하는 메소드 추가
    long countByReviewStatusAndConfirmedRiskLevel(AnalysisReviewStatus status, RiskLevel riskLevel);

    long countByReviewStatus(AnalysisReviewStatus status);
}
