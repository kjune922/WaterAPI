package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/analyses")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    @GetMapping
    public String analyses(
            @RequestParam(value = "riskLevel", required = false)RiskLevel riskLevel,
            @PageableDefault(size = 10, sort = "analyzedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            Model model
    ){

        Page<AiAnalysis> analyses = aiAnalysisService.findAnalysisPage(riskLevel, pageable);

        model.addAttribute("analyses", analyses);
        model.addAttribute("riskLevels", RiskLevel.values());
        model.addAttribute("selectedRiskLevel", riskLevel);

        return "analyses/list";
    }
}
