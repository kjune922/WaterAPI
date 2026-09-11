package com.kjune922.waterapi.client;

import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import org.springframework.stereotype.Component;

@Component
public class StubInspectionAiClient implements InspectionAiClient{
    @Override
    public InspectionAnalysisResponse analyze(String inspectionContent) {
        return new InspectionAnalysisResponse(
                "점검 내용에 대한 임시 분석 결과입니다.",
                "UNKNOWN",
                RiskLevel.CAUTION,
                "담당자가 시설 상태를 직접 확인하세요."
        );
    }
}
