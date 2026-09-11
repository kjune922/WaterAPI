package com.kjune922.waterapi.dto;

import com.kjune922.waterapi.domain.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InspectionAnalysisResponse {

    private String summary;
    private String abnormalityType;
    private RiskLevel riskLevel;
    private String recommendedAction;
}
