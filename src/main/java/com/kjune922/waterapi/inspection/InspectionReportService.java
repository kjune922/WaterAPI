package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysisRepository;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final AiAnalysisRepository aiAnalysisRepository;

    @Transactional
    public InspectionReport registerInspection(Long facilityId, String content, LocalDate inspectionDate) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("시설을 찾을 수 없습니다."));

        InspectionReport report = new InspectionReport(facility,content,inspectionDate);
        return inspectionReportRepository.save(report);
    }

    public List<InspectionReport> findInspections() {
        return inspectionReportRepository.findAll();
    }

    public InspectionReport findInspection(Long id) {
        return inspectionReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
    }

    // 상태 변경 메소드 추가
    @Transactional
    public void changeProcessingStatus(Long inspectionId, ProcessingStatus processingStatus){
        InspectionReport inspection = inspectionReportRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
        inspection.changeProcessingStatus(processingStatus);
    }

    // 점검일지 업데이트 메소드 추가
    @Transactional
    public void updateInspection(Long inspectionId, String content, LocalDate inspectionDate){
        InspectionReport inspection = inspectionReportRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
        inspection.update(content, inspectionDate);

        aiAnalysisRepository.deleteByInspectionReport_Id(inspectionId);
    }

    // 점검일지 삭제 메소드 추가
    @Transactional
    public void deleteInspection(Long inspectionId) {
        InspectionReport inspection = inspectionReportRepository.findById(inspectionId)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));

        aiAnalysisRepository.deleteByInspectionReport_Id(inspectionId);

        inspectionReportRepository.delete(inspection);
    }

    // 페이지 조회 메소드 추가
    @Transactional(readOnly = true)
    public Page<InspectionReport> findInspectionPage(ProcessingStatus processingStatus, Pageable pageable){
        if(processingStatus == null) {
            return inspectionReportRepository.findAll(pageable);
        }
        return inspectionReportRepository.findByProcessingStatus(processingStatus, pageable);
    }
}
