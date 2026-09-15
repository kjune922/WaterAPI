package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.facility.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/inspections")
public class InspectionReportController {

    private final InspectionReportService inspectionReportService;
    private final FacilityService facilityService;
    private final AiAnalysisService aiAnalysisService;

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
        model.addAttribute(
                "inspectionForm",
                new InspectionReportCreateForm()
        );

        model.addAttribute(
                "facilities",
                facilityService.findFacilities()
        );

        return "inspections/new";
    }

    @PostMapping
    public String create(
            @Validated
            @ModelAttribute("inspectionForm")
            InspectionReportCreateForm form,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute(
                    "facilities",
                    facilityService.findFacilities()
            );

            return "inspections/new";
        }

        inspectionReportService.registerInspection(
                form.getFacilityId(),
                form.getContent(),
                form.getInspectionDate()
        );

        return "redirect:/inspections";
    }

    @GetMapping("/{id}")
    public String inspectionDetail(
            @PathVariable("id") Long id,
            Model model
    ) {
        InspectionReport inspection =
                inspectionReportService.findInspection(id);

        AiAnalysis analysis =
                aiAnalysisService.findAnalysis(id)
                        .orElse(null);

        model.addAttribute("analysis", analysis);
        model.addAttribute("inspection", inspection);

        return "inspections/detail";
    }

    @GetMapping("/{id}/edit")
    public String updateForm(
            @PathVariable("id") Long id,
            Model model
    ) {
        InspectionReport inspection =
                inspectionReportService.findInspection(id);

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

    @PostMapping("/{id}/analysis")
    public String analyze(
            @PathVariable("id") Long id
    ) {
        aiAnalysisService.analyzeInspection(id);

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