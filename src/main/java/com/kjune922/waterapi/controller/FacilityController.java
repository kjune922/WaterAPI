package com.kjune922.waterapi.controller;

import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.form.FacilityCreateForm;
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
@RequestMapping("/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    @GetMapping
    public String facilities(Model model){
        model.addAttribute(
                "facilities",
                facilityService.findFacilities()
        );
        return "facilities/list";
    }

    @GetMapping("/new")
    public String createForm(Model model){
        model.addAttribute(
                "facilityForm",
                new FacilityCreateForm()
        );
        model.addAttribute(
                "facilityTypes",
                FacilityType.values()
        );
        return "facilities/new";
    }

    @PostMapping
    public String create(@Validated @ModelAttribute("facilityForm") FacilityCreateForm form,
                         BindingResult bindingResult, Model model){
        if(bindingResult.hasErrors()) {
            model.addAttribute("facilityTypes", FacilityType.values());
            return "facilities/new";
        }

        facilityService.registerFacility(
                form.getName(),
                form.getFacilityType(),
                form.getLocation()
        );
        return "redirect:/facilities";
    }
}
