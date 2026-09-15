package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.service.FacilityService;
import lombok.RequiredArgsConstructor;
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
    public String inspections(Model model) {
        model.addAttribute("inspections", inspectionReportService.findInspections());
        return "inspections/list";
    }

    @GetMapping("/new")
    public String createForm(Model model) {
        model.addAttribute("inspectionForm", new InspectionReportCreateForm());
        model.addAttribute("facilities",facilityService.findFacilities());
        return "inspections/new";
    }

    @PostMapping
    public String create(@Validated @ModelAttribute("inspectionForm") InspectionReportCreateForm form
    , BindingResult bindingResult, Model model) {
        if(bindingResult.hasErrors()) {
            model.addAttribute("facilities", facilityService.findFacilities());
            return "inspections/new";
        }
        inspectionReportService.registerInspection(form.getFacilityId(), form.getContent(), form.getInspectionDate());

        return "redirect:/inspections";
    }

    @GetMapping("/{id}")
    public String inspectionDetail(@PathVariable("id") Long id, Model model){
        InspectionReport inspection = inspectionReportService.findInspection(id);

        AiAnalysis analysis = aiAnalysisService.findAnalysis(id).orElse(null);

        model.addAttribute("analysis", analysis);

        model.addAttribute("inspection", inspection);

        return "inspections/detail";
    }

    @PostMapping("/{id}/analysis")
    public String analyze(@PathVariable("id") Long id){
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
}
