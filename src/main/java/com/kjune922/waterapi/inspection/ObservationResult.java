package com.kjune922.waterapi.inspection;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ObservationResult {
    OK("이상 없음"), ACTION_NEEDED("조치 필요"), UNCHECKED("미점검"), NOT_APPLICABLE("해당 없음");

    private final String label;
}
