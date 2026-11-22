package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.inspection.*;

public final class AnalysisInput {
    private AnalysisInput() {}

    public static String from(InspectionReport report) {
        if (!report.isChecklist()) {
            if (!hasObservation(report.getContent())) throw missingObservation();
            return report.getContent();
        }
        boolean observed = report.getAnswers().stream().anyMatch(a ->
                a.getResult() == ObservationResult.OK || a.getResult() == ObservationResult.ACTION_NEEDED
                        || hasDetail(a.getReason()));
        if (!observed && !hasDetail(report.getNotes()) && !hasDetail(report.getActionTaken())) {
            throw missingObservation();
        }
        StringBuilder input = new StringBuilder("작성 당시 시설 유형: ").append(report.getRecordedType());
        for (InspectionAnswer answer : report.getAnswers()) {
            input.append("\n").append(answer.getLabel()).append(": ").append(answer.getResult().getLabel());
            if (answer.getReason() != null && !answer.getReason().isBlank()) {
                input.append(" (사유: ").append(answer.getReason()).append(")");
            }
        }
        return input.append("\n특이사항: ").append(report.getNotes() == null ? "" : report.getNotes())
                .append("\n실시한 조치: ").append(report.getActionTaken() == null ? "" : report.getActionTaken()).toString();
    }

    private static boolean hasDetail(String value) {
        if (!hasObservation(value)) return false;
        String compact = value.replaceAll("[\\s\\p{Punct}]", "");
        return !compact.matches("(특이사항|조치|관찰내용|해당사항|이상)?(없음|없습니다|미점검|해당없음)|점검했습니다");
    }

    private static boolean hasObservation(String text) {
        String letters = text == null ? "" : text.replaceAll("[^가-힣a-zA-Z]", "");
        return letters.length() >= 2 && !letters.matches("(.)\\1*");
    }

    private static IllegalArgumentException missingObservation() {
        return new IllegalArgumentException("시설 상태나 관찰한 이상을 구체적으로 입력한 뒤 분석해 주세요.");
    }
}
