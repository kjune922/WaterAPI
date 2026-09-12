package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.client.InspectionAiClient;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.inspection.InspectionReport;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisService {

    private final InspectionReportRepository inspectionReportRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final InspectionAiClient inspectionAiClient;

    @Transactional
    public AiAnalysis analyzeInspection(Long inspectionReportId) {
        InspectionReport inspectionReport = inspectionReportRepository.findById(inspectionReportId)
                .orElseThrow(() -> new IllegalArgumentException("점검일지를 찾을 수 없습니다."));

        // 이미있으면?
        if(aiAnalysisRepository.existsByInspectionReportId(inspectionReportId)){
            throw new IllegalArgumentException("이미 AI 분석이 완료된 점검일지입니다.");
        }

        InspectionAnalysisResponse response = inspectionAiClient.analyze(inspectionReport.getContent());

        AiAnalysis aiAnalysis = new AiAnalysis(
                inspectionReport,
                response.getSummary(),
                response.getAbnormalityType(),
                response.getRiskLevel(),
                response.getRecommendedAction(),
                LocalDateTime.now()
        );

        return aiAnalysisRepository.save(aiAnalysis);
    }

    public Optional<AiAnalysis> findAnalysis(Long inspectionReportId) {
        return aiAnalysisRepository.findByInspectionReportId(inspectionReportId);
    }

    public List<AiAnalysis> findAnalysisByRiskLevel(RiskLevel riskLevel){

        if(riskLevel == null){
            return aiAnalysisRepository.findAll();
        }
        return aiAnalysisRepository.findAllByRiskLevel(riskLevel);
    }
}
