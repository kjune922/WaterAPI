package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.exception.DuplicateAnalysisException;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AnalysisResultWriter {
    private final InspectionReportRepository reports;
    private final AiAnalysisRepository analyses;

    @Transactional
    public AiAnalysis save(Long id, long inputRevision, InspectionAnalysisResponse response) {
        long current = reports.lockRevision(id)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
        if (current != inputRevision) {
            throw new DuplicateAnalysisException("분석 중 점검일지가 수정되었습니다. 현재 기록으로 다시 분석해 주세요.");
        }
        if (analyses.existsByInspectionReportId(id)) {
            throw new DuplicateAnalysisException("이미 AI 분석이 완료된 점검일지입니다.");
        }
        return analyses.save(new AiAnalysis(reports.getReferenceById(id), response.getSummary(),
                response.getAbnormalityType(), response.getRiskLevel(), response.getRecommendedAction(),
                LocalDateTime.now()));
    }
}
