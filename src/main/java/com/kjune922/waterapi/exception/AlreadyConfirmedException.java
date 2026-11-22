package com.kjune922.waterapi.exception;

public class AlreadyConfirmedException extends RuntimeException {
    public AlreadyConfirmedException() {
        super("이미 확정된 분석입니다. 상세 화면에서 확정본을 확인해 주세요.");
    }
}
