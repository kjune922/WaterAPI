package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // 이미 분석된 점검일지 인지 체크
    boolean existsByInspectionReportId(Long inspectionReportId);

    // 특정 점검일지의 분석 결과 조회
    Optional<AiAnalysis> findByInspectionReportId(Long inspectionReportId);

    // 특정 위험도의 AI분석 결과 조회
    List<AiAnalysis> findAllByRiskLevel(RiskLevel riskLevel);

    // 페이지 조회 메소드 추가
    Page<AiAnalysis> findAllByRiskLevel(RiskLevel riskLevel, Pageable pageable);

    // 점검일지 삭제 메소드 추가
    void deleteByInspectionReport_Id(Long inspectionId);
}
