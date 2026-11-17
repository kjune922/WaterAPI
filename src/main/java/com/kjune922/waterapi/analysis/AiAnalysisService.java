package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.client.InspectionAiClient;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.exception.DuplicateAnalysisException;
import com.kjune922.waterapi.exception.OpenAiAnalysisException;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.inspection.InspectionReport;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));

        // 이미있으면?
        if(aiAnalysisRepository.existsByInspectionReportId(inspectionReportId)){
            throw new DuplicateAnalysisException("이미 AI 분석이 완료된 점검일지입니다.");
        }

        validateInspectionContent(inspectionReport.getContent());

        InspectionAnalysisResponse response = inspectionAiClient.analyze(inspectionReport.getContent());

        validateResponse(response);

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

    // 형식상 명백히 무의미한 입력만 차단한다. 내용의 충분성은 AI 응답에서 별도로 확인한다.
    private void validateInspectionContent(String content) {
        String letters = content == null ? "" : content.replaceAll("[^가-힣a-zA-Z]", "");
        if (letters.length() < 2 || letters.matches("(.)\\1*")) {
            throw new IllegalArgumentException("시설 상태나 관찰한 이상을 구체적으로 입력한 뒤 분석해 주세요.");
        }
    }

    // 외부 시스템의 응답도 DB 제약과 업무 기준을 만족해야 저장한다.
    private void validateResponse(InspectionAnalysisResponse response) {
        if (response == null || response.getRiskLevel() == null) {
            throw new OpenAiAnalysisException("AI 분석 결과의 필수 항목이 누락되었습니다.");
        }
        validateText(response.getSummary(), 500);
        validateText(response.getAbnormalityType(), 100);
        validateText(response.getRecommendedAction(), 1000);
    }

    private void validateText(String text, int maximumLength) {
        if (text == null || text.isBlank() || text.length() > maximumLength) {
            throw new OpenAiAnalysisException("AI 분석 결과의 내용 또는 길이가 저장 기준에 맞지 않습니다.");
        }
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

    // 페이지네이션 구현하는 메소드 추가
    public Page<AiAnalysis> findAnalysisPage(RiskLevel riskLevel, Pageable pageable){
        if(riskLevel == null){
            return aiAnalysisRepository.findAll(pageable);
        }

        return aiAnalysisRepository.findAllByRiskLevel(riskLevel,pageable);
    }

}
