package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionReportService {

    private final InspectionReportRepository inspectionReportRepository;
    private final FacilityRepository facilityRepository;

    @Transactional
    public InspectionReport registerInspection(Long facilityId, String content, LocalDate inspectionDate) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("시설을 찾을 수 없습니다."));

        InspectionReport report = new InspectionReport(facility,content,inspectionDate);
        return inspectionReportRepository.save(report);
    }

    public List<InspectionReport> findInspections() {
        return inspectionReportRepository.findAll();
    }

    public InspectionReport findInspection(Long id) {
        return inspectionReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("점검일지를 찾을 수 없습니다."));
    }
}
