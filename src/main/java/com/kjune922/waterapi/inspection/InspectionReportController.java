package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisConfirmForm;
import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.analysis.AnalysisReviewStatus;
import com.kjune922.waterapi.exception.AlreadyConfirmedException;
import com.kjune922.waterapi.exception.OpenAiAnalysisException;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/inspections")
public class InspectionReportController {

    private final InspectionReportService inspectionReportService;
    private final FacilityService facilityService;
    private final AiAnalysisService aiAnalysisService;

    /**
     * processingStatuses 로 전체 상태 선택지
     * selectedProcessingStatus 는 선택된 하나의 상태
     */
    @GetMapping
    public String inspections(
            @RequestParam(value = "processingStatus", required = false) ProcessingStatus processingStatus,
            @PageableDefault(size = 10, sort = {"inspectionDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable,
            Model model)
    {
        Page<InspectionReport> inspections = inspectionReportService.findInspectionPage(processingStatus, pageable);
        model.addAttribute("inspections", inspections);
        model.addAttribute("processingStatuses", ProcessingStatus.values());
        model.addAttribute("selectedProcessingStatus", processingStatus);

        return "inspections/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("facilities", facilityService.findFacilities());
        return "inspections/new";
    }

    @InitBinder("checklist")
    void bindChecklist(WebDataBinder binder) {
        binder.setAllowedFields("facilityId", "recordedType", "formVersion", "inspectionDate",
                "inspectorName", "notes", "actionTaken", "results[*]", "reasons[*]");
    }

    @GetMapping("/new/{facilityId}")
    public String checklistForm(@PathVariable Long facilityId, Model model) {
        Facility facility = facilityService.findFacility(facilityId);
        ChecklistForm form = new ChecklistForm();
        form.setFacilityId(facilityId);
        form.setRecordedType(facility.getFacilityType());
        form.setFormVersion(ChecklistItem.VERSION);
        model.addAttribute("checklist", form);
        return checklistView(model, facility, ChecklistItem.forType(facility.getFacilityType()));
    }

    @PostMapping("/new/{facilityId}")
    public String createChecklist(@PathVariable Long facilityId,
                                  @Validated @ModelAttribute("checklist") ChecklistForm form,
                                  BindingResult errors, HttpServletRequest request, Model model) {
        Facility facility = facilityService.findFacility(facilityId);
        // 작성 중 시설 유형이 바뀌어도 오류 화면에서는 제출한 양식과 답변을 보여 준다.
        var type = form.getRecordedType() == null ? facility.getFacilityType() : form.getRecordedType();
        List<ChecklistItem> items = ChecklistItem.forType(type);
        validateChecklistRequest(form, items, errors, request);
        if (!facilityId.equals(form.getFacilityId()) || facility.getFacilityType() != form.getRecordedType()
                || !Integer.valueOf(ChecklistItem.VERSION).equals(form.getFormVersion())) {
            errors.reject("changed", "시설 또는 양식이 변경되었습니다. 입력값을 확인하고 시설을 다시 선택해 주세요.");
        }
        if (!errors.hasErrors()) {
            try {
                inspectionReportService.registerChecklist(facilityId, form);
                return "redirect:/inspections";
            } catch (IllegalArgumentException changed) {
                errors.reject("changed", changed.getMessage());
            }
        }
        return checklistView(model, facility, items);
    }

    private void validateChecklistRequest(ChecklistForm form, List<ChecklistItem> items, BindingResult errors,
                                          HttpServletRequest request) {
        boolean duplicate = request.getParameterMap().entrySet().stream()
                .anyMatch(entry -> (entry.getKey().startsWith("results[") || entry.getKey().startsWith("reasons["))
                        && entry.getValue().length != 1);
        if (duplicate) errors.reject("duplicateItem", "한 항목에 여러 값이 제출되었습니다.");
        form.validateItems(items, errors);
    }

    private String checklistView(Model model, Facility facility, List<ChecklistItem> items) {
        model.addAttribute("facility", facility);
        model.addAttribute("items", items);
        model.addAttribute("resultChoices", ObservationResult.values());
        return "inspections/checklist";
    }

    @GetMapping("/{id}")
    public String inspectionDetail(
            @PathVariable("id") Long id,
            Model model
    ) {
        var detail = inspectionReportService.findDetail(id);
        AiAnalysis analysis = detail.analysis();

        if (analysis != null && analysis.getReviewStatus() == AnalysisReviewStatus.PENDING_REVIEW) {
            model.addAttribute("confirmForm", AiAnalysisConfirmForm.from(analysis));
        }
        model.addAttribute("analysis", analysis);
        model.addAttribute("inspection", detail.report());

        return "inspections/detail";
    }

    @GetMapping("/{id}/edit")
    public String updateForm(
            @PathVariable("id") Long id,
            Model model
    ) {
        InspectionReport inspection =
                inspectionReportService.findInspection(id);

        if (inspection.isChecklist()) {
            model.addAttribute("checklist", ChecklistForm.from(inspection));
            model.addAttribute("inspectionId", id);
            return checklistView(model, inspection.getFacility(), inspection.savedItems());
        }

        InspectionReportUpdateForm form =
                new InspectionReportUpdateForm();

        form.setContent(inspection.getContent());
        form.setInspectionDate(
                inspection.getInspectionDate()
        );

        model.addAttribute("inspectionForm", form);
        model.addAttribute("inspectionId", id);

        return "inspections/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") Long id,
            @Validated
            @ModelAttribute("inspectionForm")
            InspectionReportUpdateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("inspectionId", id);

            return "inspections/edit";
        }

        inspectionReportService.updateInspection(
                id,
                form.getContent(),
                form.getInspectionDate()
        );

        return "redirect:/inspections/" + id;
    }

    @PostMapping("/{id}/checklist")
    public String editChecklist(@PathVariable Long id, @Validated @ModelAttribute("checklist") ChecklistForm form,
                                BindingResult errors, HttpServletRequest request, Model model) {
        InspectionReport report = inspectionReportService.findInspection(id);
        if (!report.isChecklist()) throw new IllegalArgumentException("자유서술형 점검일지입니다.");
        List<ChecklistItem> items = report.savedItems();
        validateChecklistRequest(form, items, errors, request);
        if (!errors.hasErrors()) {
            try {
                inspectionReportService.updateChecklist(id, form);
                return "redirect:/inspections/" + id;
            } catch (IllegalArgumentException changed) {
                errors.reject("changed", changed.getMessage());
            }
        }
        model.addAttribute("inspectionId", id);
        return checklistView(model, report.getFacility(), items);
    }

    @PostMapping("/{id}/analysis")
    public String analyze(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            aiAnalysisService.analyzeInspection(id);
        } catch (OpenAiAnalysisException failure) {
            redirect.addFlashAttribute("analysisMessage", "AI 분석 실패 · 다시 시도해 주세요");
        } catch (IllegalArgumentException insufficient) {
            redirect.addFlashAttribute("analysisMessage", "입력 보완 필요 · " + insufficient.getMessage());
        }
        return "redirect:/inspections/" + id;
    }

    @PostMapping("/{id}/analysis/{analysisId}/confirm")
    public String confirm(@PathVariable Long id, @PathVariable Long analysisId,
                          @Validated @ModelAttribute("confirmForm") AiAnalysisConfirmForm form,
                          BindingResult errors, Model model, HttpServletResponse response) {
        if (errors.hasErrors()) {
            var detail = inspectionReportService.findDetail(id);
            AiAnalysis analysis = detail.analysis();
            if (analysis == null || !analysisId.equals(analysis.getId())) {
                throw new ResourceNotFoundException("해당 점검일지의 분석을 찾을 수 없습니다.");
            }
            if (analysis.getReviewStatus() == AnalysisReviewStatus.CONFIRMED) {
                throw new AlreadyConfirmedException();
            }
            model.addAttribute("analysis", analysis);
            model.addAttribute("inspection", detail.report());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return "inspections/detail";
        }
        aiAnalysisService.confirm(id, analysisId, form);
        return "redirect:/inspections/" + id;
    }

    @PostMapping("/{id}/status")
    public String changeProcessingStatus(
            @PathVariable("id") Long id,
            @RequestParam("processingStatus")
            ProcessingStatus processingStatus
    ) {
        inspectionReportService.changeProcessingStatus(
                id,
                processingStatus
        );

        return "redirect:/inspections/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id) {
        inspectionReportService.deleteInspection(id);
        return "redirect:/inspections";
    }
}