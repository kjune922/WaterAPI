package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityRepository;
import com.kjune922.waterapi.facility.FacilityType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class InspectionReportServiceTest {

    @Autowired
    private InspectionReportService inspectionReportService;

    @Autowired
    private FacilityRepository facilityRepository;

    @Test
    void 점검일지_등록 () {
        Facility facility = facilityRepository.save(new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 수영장"
        ));

        InspectionReport report = inspectionReportService.registerInspection(
                facility.getId(),
                "평소보다 진동이 큼",
                LocalDate.of(2026,9,10)
        );

        assertThat(report.getId()).isNotNull();
        assertThat(report.getFacility().getName()).isEqualTo("2번 펌프");
        assertThat(report.getContent()).isEqualTo("평소보다 진동이 큼");
        assertThat(report.getInspectionDate()).isEqualTo(LocalDate.of(2026,9,10));
        assertThat(report.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
    }

    @Test
    void 존재하지않는_시설의점검일지_조회불가(){
        assertThatThrownBy(() -> inspectionReportService.registerInspection(
                999L,
                "점검 내용",
                LocalDate.of(2026,9,10)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시설을 찾을 수 없습니다.");
    }

    @Test
    void 존재하지않는_점검일지_조회불가 () {
        assertThatThrownBy(() -> inspectionReportService.findInspection(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("점검일지를 찾을 수 없습니다.");
    }

}