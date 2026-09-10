package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@NoArgsConstructor
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

    public InspectionReport(Facility facility, String content, LocalDate inspectionDate) {

        validateFacility(facility);
        validateContent(content);
        validateInspectionDate(inspectionDate);

        this.facility = facility;
        this.content = content;
        this.inspectionDate = inspectionDate;
        this.processingStatus = processingStatus.PENDING;
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

}
