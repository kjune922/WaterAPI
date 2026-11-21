package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiAnalysisConfirmForm {
    @NotBlank(message = "요약 내용을 입력해 주세요.")
    @Size(max = 500, message = "500자 이내로 입력해 주세요.")
    private String summary;

    @NotBlank(message = "이상 유형 내용을 입력해 주세요.")
    @Size(max = 100, message = "100자 이내로 입력해 주세요.")
    private String abnormalityType;

    @NotNull
    private RiskLevel riskLevel;

    @NotBlank(message = "권장 조치 내용을 입력해 주세요.")
    @Size(max = 1000, message = "1000자 이내로 입력해 주세요.")
    private String recommendedAction;

    public static AiAnalysisConfirmForm from(AiAnalysis analysis) {
        AiAnalysisConfirmForm form = new AiAnalysisConfirmForm();
        form.setSummary(analysis.getSummary());
        form.setAbnormalityType(analysis.getAbnormalityType());
        form.setRiskLevel(analysis.getRiskLevel());
        form.setRecommendedAction(analysis.getRecommendedAction());
        return form;
    }
}
