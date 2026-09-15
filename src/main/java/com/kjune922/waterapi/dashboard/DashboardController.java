package com.kjune922.waterapi.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/")
    public String dashboard(Model model){
        model.addAttribute("summary", dashboardService.getSummary());
        return "dashboard/index";
    }
}
