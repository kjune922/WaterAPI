package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "inspection_reports")
public class InspectionReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private LocalDate inspectionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingStatus processingStatus;

    private Integer formVersion;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 32)
    private FacilityType recordedType;
    @Column(length = 50)
    private String inspectorName;
    @Column(length = 2000)
    private String notes;
    @Column(length = 2000)
    private String actionTaken;
    @ColumnDefault("0")
    @Column(nullable = false)
    private long inputRevision;

    @ElementCollection
    @CollectionTable(name = "inspection_answers", joinColumns = @JoinColumn(name = "inspection_id"))
    @OrderColumn(name = "item_order")
    private List<InspectionAnswer> answers = new ArrayList<>();

    public boolean isChecklist() {
        return formVersion != null;
    }

    public String getListDescription() {
        if (!isChecklist()) return content;
        return notes == null || notes.isBlank() ? recordedType + " 점검표" : notes;
    }

    public List<ChecklistItem> savedItems() {
        return answers.stream().map(a -> new ChecklistItem(a.getCode(), a.getLabel())).toList();
    }

    public InspectionReport(Facility facility, ChecklistForm form) {
        this.facility = facility;
        // 자유서술 필드는 이전 DB의 NOT NULL 제약을 유지한다. 점검표의 관찰은 answers에 저장한다.
        this.content = "";
        this.recordedType = facility.getFacilityType();
        this.formVersion = ChecklistItem.VERSION;
        this.processingStatus = ProcessingStatus.PENDING;
        applyChecklist(form, ChecklistItem.forType(recordedType));
    }

    public void updateChecklist(ChecklistForm form) {
        applyChecklist(form, savedItems());
        inputRevision++;
    }

    private void applyChecklist(ChecklistForm form, List<ChecklistItem> items) {
        if (!facility.getId().equals(form.getFacilityId()) || recordedType != form.getRecordedType()
                || !formVersion.equals(form.getFormVersion())) {
            throw new IllegalArgumentException("시설 또는 양식이 바뀌었습니다. 양식을 다시 확인해 주세요.");
        }
        inspectionDate = form.getInspectionDate();
        inspectorName = form.getInspectorName().strip();
        notes = form.getNotes();
        actionTaken = form.getActionTaken();
        answers.clear();
        answers.addAll(form.answers(items));
    }

    public InspectionReport(Facility facility, String content, LocalDate inspectionDate) {

        validateFacility(facility);
        validateContent(content);
        validateInspectionDate(inspectionDate);

        this.facility = facility;
        this.content = content;
        this.inspectionDate = inspectionDate;
        this.processingStatus = ProcessingStatus.PENDING;
    }

    private void validateInspectionDate(LocalDate inspectionDate) {
        if(inspectionDate == null){
            throw new IllegalArgumentException("점검 일자는 필수입니다.");
        }
    }

    private void validateContent(String content) {
        if(content == null || content.isBlank()){
            throw new IllegalArgumentException("점검 내용은 비어 있을 수 없습니다.");
        }
    }

    private void validateFacility(Facility facility) {
        if(facility == null) {
            throw new IllegalArgumentException("점검 시설은 필수입니다.");
        }
    }

    // 점검일지 수정 메소드 추가
    public void update(String content, LocalDate inspectionDate){
        validateContent(content);
        validateInspectionDate(inspectionDate);

        if (isChecklist()) throw new IllegalArgumentException("유형별 점검표로 수정해 주세요.");
        this.content = content;
        this.inspectionDate = inspectionDate;
        inputRevision++;
    }

    // 점검 처리 상태 변경 메소드 추가
    public void changeProcessingStatus(ProcessingStatus processingStatus){
        if(processingStatus == null){
            throw new IllegalArgumentException("처리 상태는 필수입니다.");
        }
        this.processingStatus = processingStatus;
    }

}
