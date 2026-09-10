package com.kjune922.waterapi.inspection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class InspectionReportCreateForm {

    @NotNull(message = "점검 시설을 선택해주세요.")
    private Long facilityId;

    @NotBlank(message = "점검 내용을 입력해주세요.")
    @Size(max = 2000, message = "점검 내용은 2000자 이하여야 합니다.")
    private String content;

    @NotNull(message = "점검 일자를 입력해주세요.")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate inspectionDate;
}
