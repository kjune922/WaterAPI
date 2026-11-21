package com.kjune922.waterapi.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AnalysisReviewStatus {
    PENDING_REVIEW("검토 대기"),
    CONFIRMED("확정");

    private final String description;
}
