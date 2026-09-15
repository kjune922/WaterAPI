package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalyses, Long> {

    // 이미 분석된 점검일지 인지 체크
    boolean existsByInspectionReportId(Long inspectionReportId);

    // 특정 점검일지의 분석 결과 조회
    Optional<AiAnalyses> findByInspectionReportId(Long inspectionReportId);

    // 특정 위험도의 AI분석 결과 조회
    List<AiAnalyses> findAllByRiskLevel(RiskLevel riskLevel);
}
