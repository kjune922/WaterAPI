package com.kjune922.waterapi.inspection;

public enum ProcessingStatus {
    PENDING("대기"), IN_PROGRESS("처리 중"), COMPLETED("처리 완료");

    private final String description;

    ProcessingStatus(String description){
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
