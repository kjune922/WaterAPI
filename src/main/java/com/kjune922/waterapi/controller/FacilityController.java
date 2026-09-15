package com.kjune922.waterapi.controller;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.form.FacilityCreateForm;
import com.kjune922.waterapi.facility.FacilityService;
import com.kjune922.waterapi.form.FacilityUpdateForm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}/edit")
    public String updateForm(@PathVariable("id") Long id, Model model) {
        Facility facility = facilityService.findFacility(id);

        FacilityUpdateForm form = new FacilityUpdateForm();

        form.setName(facility.getName());
        form.setFacilityType(facility.getFacilityType());
        form.setLocation(facility.getLocation());

        model.addAttribute("facilityForm", form);
        model.addAttribute("facilityTypes", FacilityType.values());
        model.addAttribute("facilityId", id);

        return "facilities/edit";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable("id") Long id,
            @Validated @ModelAttribute("facilityForm") FacilityUpdateForm form,
            BindingResult bindingResult, Model model) {

        if(bindingResult.hasErrors()){
            model.addAttribute("facilityTypes", FacilityType.values());
            model.addAttribute("facilityId",id);

            return "facilities/edit";
        }

        facilityService.updateFacility(id,form.getName(), form.getFacilityType(), form.getLocation());
        return "redirect:/facilities";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable("id") Long id){
        facilityService.deleteFacility(id);

        return "redirect:/facilities";
    }
}
