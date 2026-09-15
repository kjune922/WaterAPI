package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@WebMvcTest(AiAnalysisController.class)
class AiAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAnalysisService aiAnalysisService;

    @Test
    @DisplayName("AI분석 결과 전체 페이지 조회")
    void readResultAll() throws Exception{
        given(aiAnalysisService.findAnalysisPage(isNull(), any(Pageable.class)))
                .willReturn(Page.empty());

        mockMvc.perform(get("/analyses"))
                .andExpect(status().isOk())
                .andExpect(view().name("analyses/list"))
                .andExpect(model().attributeExists("analyses"))
                .andExpect(model().attributeExists("riskLevels"));

        verify(aiAnalysisService).findAnalysisPage(isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("위험도로 AI분석 결과 페이지 조회")
    void readAllResultByRiskLevel() throws Exception{
        given(aiAnalysisService.findAnalysisPage(eq(RiskLevel.WARNING), any(Pageable.class)))
                .willReturn(Page.empty());

        mockMvc.perform(get("/analyses")
                .param("riskLevel", "WARNING")
                .param("page", "0")
                .param("size","10"))
                .andExpect(status().isOk())
                .andExpect(view().name("analyses/list"))
                .andExpect(model().attribute("selectedRiskLevel", RiskLevel.WARNING));

        verify(aiAnalysisService).findAnalysisPage(eq(RiskLevel.WARNING), any(Pageable.class));
    }

}