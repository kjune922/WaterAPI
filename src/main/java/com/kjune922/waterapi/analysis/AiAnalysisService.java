package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.client.InspectionAiClient;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.exception.AlreadyConfirmedException;
import com.kjune922.waterapi.exception.DuplicateAnalysisException;
import com.kjune922.waterapi.exception.OpenAiAnalysisException;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.inspection.InspectionReport;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiAnalysisService {

    private final InspectionReportRepository inspectionReportRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final InspectionAiClient inspectionAiClient;
    private final AnalysisResultWriter resultWriter;

    @Transactional(propagation = Propagation.NEVER)
    public AiAnalysis analyzeInspection(Long inspectionReportId) {
        InspectionReport inspectionReport = inspectionReportRepository.findWithAnswers(inspectionReportId)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));

        if(aiAnalysisRepository.existsByInspectionReportId(inspectionReportId)){
            throw new DuplicateAnalysisException("이미 AI 분석이 완료된 점검일지입니다.");
        }

        long revision = inspectionReport.getInputRevision();
        String input = AnalysisInput.from(inspectionReport);
        InspectionAnalysisResponse response = inspectionAiClient.analyze(input);
        validateResponse(response);
        return resultWriter.save(inspectionReportId, revision, response);
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

    @Transactional
    public void confirm(Long inspectionId, Long analysisId, @NotNull @Valid AiAnalysisConfirmForm form) {
        int changed = aiAnalysisRepository.confirmPending(inspectionId, analysisId, form.getSummary(),
                form.getAbnormalityType(), form.getRiskLevel(), form.getRecommendedAction(), LocalDateTime.now());
        if (changed == 1) {
            return;
        }
        if (aiAnalysisRepository.existsByIdAndInspectionReportId(analysisId, inspectionId)) {
            throw new AlreadyConfirmedException();
        }
        throw new ResourceNotFoundException("해당 점검일지의 분석을 찾을 수 없습니다.");
    }

    public Optional<AiAnalysis> findAnalysis(Long inspectionReportId) {
        return aiAnalysisRepository.findByInspectionReportId(inspectionReportId);
    }

    public List<AiAnalysis> findAnalysisByRiskLevel(RiskLevel riskLevel) {
        return findAnalysisPage(riskLevel, null, Pageable.unpaged()).getContent();
    }

    public Page<AiAnalysis> findAnalysisPage(RiskLevel riskLevel, AnalysisReviewStatus status, Pageable pageable) {
        return aiAnalysisRepository.search(riskLevel, status, pageable);
    }
}
