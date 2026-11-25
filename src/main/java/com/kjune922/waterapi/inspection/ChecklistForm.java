package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.FacilityType;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.Errors;

import java.time.LocalDate;
import java.util.*;

@Getter
@Setter
public class ChecklistForm {
    @NotNull private Long facilityId;
    @NotNull private FacilityType recordedType;
    @NotNull private Integer formVersion;
    @NotNull(message = "점검 일자를 입력해 주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate inspectionDate;
    @NotBlank(message = "점검자 이름을 입력해 주세요.")
    @Size(max = 50) private String inspectorName;
    @Size(max = 2000) private String notes;
    @Size(max = 2000) private String actionTaken;
    private Map<String, ObservationResult> results = new LinkedHashMap<>();
    private Map<String, String> reasons = new LinkedHashMap<>();

    public void validateItems(List<ChecklistItem> items, Errors errors) {
        Set<String> allowed = new HashSet<>();
        for (ChecklistItem item : items) {
            String code = item.code();
            allowed.add(code);
            ObservationResult result = results.get(code);
            String reason = Objects.toString(reasons.get(code), "");
            if (result == null) {
                errors.rejectValue("results[" + code + "]", "required", "관찰 결과를 선택해 주세요.");
            }
            if (result == ObservationResult.ACTION_NEEDED && reason.isBlank()) {
                errors.rejectValue("reasons[" + code + "]", "required", "조치가 필요한 이유를 입력해 주세요.");
            } else if (reason.length() > 500) {
                errors.rejectValue("reasons[" + code + "]", "length", "사유는 500자 이내로 입력해 주세요.");
            }
        }
        if (!allowed.containsAll(results.keySet()) || !allowed.containsAll(reasons.keySet())) {
            errors.reject("unexpectedItem", "이 양식에 없는 점검 항목입니다.");
        }
    }

    public List<InspectionAnswer> answers(List<ChecklistItem> items) {
        return items.stream().map(item -> new InspectionAnswer(item, results.get(item.code()),
                Objects.toString(reasons.get(item.code()), "").strip())).toList();

        /**
         *     for (ChecklistItem item : items) {
         *         // 질문 코드에 해당하는 선택 결과
         *         ObservationResult result = results.get(item.code());
         *
         *         // 질문 코드에 해당하는 사유
         *         String reason = Objects.toString(
         *                 reasons.get(item.code()), ""
         *         ).strip();
         *
         *         // 질문 + 결과 + 사유를 하나의 객체로 묶음
         *         InspectionAnswer answer =
         *                 new InspectionAnswer(item, result, reason);
         *
         *         answerList.add(answer);
         */

    }

    public static ChecklistForm from(InspectionReport report) {
        ChecklistForm form = new ChecklistForm();
        form.facilityId = report.getFacility().getId();
        form.recordedType = report.getRecordedType();
        form.formVersion = report.getFormVersion();
        form.inspectionDate = report.getInspectionDate();
        form.inspectorName = report.getInspectorName();
        form.notes = report.getNotes();
        form.actionTaken = report.getActionTaken();
        for (InspectionAnswer answer : report.getAnswers()) {
            form.results.put(answer.getCode(), answer.getResult());
            form.reasons.put(answer.getCode(), answer.getReason());
        }
        return form;
    }
}
