package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/inspections")
public class InspectionReportController {

    private final InspectionReportService inspectionReportService;
    private final FacilityService facilityService;

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
}
