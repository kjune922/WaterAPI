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
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class InspectionReportRepositoryTest {

    @Autowired
    private InspectionReportRepository inspectionReportRepository;

    @Autowired
    private FacilityRepository facilityRepository;

    @Test
    void 점검일지_작성 () {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 수영장"
        );
        facilityRepository.save(facility);

        InspectionReport report = new InspectionReport(
                facility,
                "평소보다 진동이 큼",
                LocalDate.of(2026,9,10)
        );

        InspectionReport savedReport = inspectionReportRepository.save(report);

        assertThat(savedReport.getId()).isNotNull();
        assertThat(savedReport.getFacility().getName()).isEqualTo("2번 펌프");
        assertThat(savedReport.getContent()).isEqualTo("평소보다 진동이 큼");
        assertThat(savedReport.getInspectionDate()).isEqualTo(LocalDate.of(2026,9,10));
        assertThat(savedReport.getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
    }

}