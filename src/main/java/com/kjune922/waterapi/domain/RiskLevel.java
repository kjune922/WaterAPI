package com.kjune922.waterapi.domain;

public enum RiskLevel {

    NORMAL("정상"),
    CAUTION("주의"),
    WARNING("위험");

    private final String description;

    RiskLevel(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
