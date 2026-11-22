package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisRepository;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionReportService {

    private final InspectionReportRepository inspectionReportRepository;
    private final FacilityRepository facilityRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    private final EntityManager entityManager;

    @Transactional
    public InspectionReport registerChecklist(Long facilityId, @Valid ChecklistForm form) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("시설을 찾을 수 없습니다."));
        entityManager.refresh(facility, LockModeType.PESSIMISTIC_READ);
        validateChecklist(form, ChecklistItem.forType(facility.getFacilityType()));
        return inspectionReportRepository.save(new InspectionReport(facility, form));
    }

    @Transactional
    public void updateChecklist(Long id, @Valid ChecklistForm form) {
        InspectionReport report = lockedReport(id);
        if (!report.isChecklist()) throw new IllegalArgumentException("자유서술형 점검일지입니다.");
        validateChecklist(form, report.savedItems());
        report.updateChecklist(form);
        aiAnalysisRepository.deleteByInspectionReport_Id(id);
    }

    private void validateChecklist(ChecklistForm form, List<ChecklistItem> items) {
        var errors = new BeanPropertyBindingResult(form, "checklist");
        form.validateItems(items, errors);
        if (errors.hasErrors()) throw new IllegalArgumentException(errors.getAllErrors().get(0).getDefaultMessage());
    }

    private InspectionReport lockedReport(Long id) {
        InspectionReport report = inspectionReportRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
        // 수정 화면에서 먼저 읽은 값이 남아 있어도 잠근 행의 최신 값으로 갱신한다.
        entityManager.refresh(report, LockModeType.PESSIMISTIC_WRITE);
        return report;
    }

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
        return inspectionReportRepository.findWithAnswers(id)
                .orElseThrow(() -> new ResourceNotFoundException("점검일지를 찾을 수 없습니다."));
    }

    public record Detail(InspectionReport report, AiAnalysis analysis) {}

    // 두 조회 사이에 일지 수정과 재분석이 완료되어도 처음 읽은 시점의 결과를 보여 준다.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Detail findDetail(Long id) {
        InspectionReport report = findInspection(id);
        return new Detail(report, aiAnalysisRepository.findByInspectionReportId(id).orElse(null));
    }

    // 상태 변경 메소드 추가
    @Transactional
    public void changeProcessingStatus(Long inspectionId, ProcessingStatus processingStatus){
        InspectionReport inspection = lockedReport(inspectionId);
        inspection.changeProcessingStatus(processingStatus);
    }

    // 점검일지 업데이트 메소드 추가

    /**
     * 수정 요청이 성공하면 기존 분석을 무효화한다. 내용과 날짜가 같아도 적용한다.
     * 내용/날짜 변경과 분석 삭제는 한 트랜잭션으로 성공하거나 함께 롤백한다.
     * 처리 상태는 현장 업무 상태이므로 분석 삭제로 초기화하지 않는다.
     */
    @Transactional
    public void updateInspection(Long inspectionId, String content, LocalDate inspectionDate){
        InspectionReport inspection = lockedReport(inspectionId);
        inspection.update(content, inspectionDate);

        aiAnalysisRepository.deleteByInspectionReport_Id(inspectionId);
    }

    // 점검일지 삭제 메소드 추가
    @Transactional
    public void deleteInspection(Long inspectionId) {
        InspectionReport inspection = lockedReport(inspectionId);

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
