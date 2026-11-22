package com.kjune922.waterapi.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisReviewStatus {
    PENDING_REVIEW("AI 분석 완료 · 사용자 검토 대기 중"),
    CONFIRMED("사용자 검토·확정 완료");

    private final String description;
}
