package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.exception.OpenAiAnalysisException;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityService;
import com.kjune922.waterapi.facility.FacilityType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InspectionReportController.class)
class InspectionReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionReportService inspectionReportService;

    @MockitoBean
    private FacilityService facilityService;

    @MockitoBean
    private AiAnalysisService aiAnalysisService;

    @Test
    void 점검일지_목록_조회 () throws Exception {
        given(inspectionReportService.findInspectionPage(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .willReturn(org.springframework.data.domain.Page.empty());

        mockMvc.perform(get("/inspections"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspections/list"))
                .andExpect(model().attributeExists("inspections"));
    }

    @Test
    void 점검일지_등록화면_조회() throws Exception {
        Facility facility = new Facility("2번 펌프", FacilityType.PUMP, "부산");

        given(facilityService.findFacilities())
                .willReturn(List.of(facility));

        mockMvc.perform(get("/inspections/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspections/new"))
                .andExpect(model().attributeExists("facilities"));
    }

    @Test
    void 점검일지를_등록한다() throws Exception {
        given(facilityService.findFacility(1L)).willReturn(pump());
        mockMvc.perform(pumpRequest("OK"))
                .andExpect(redirectedUrl("/inspections"));
        verify(inspectionReportService).registerChecklist(eq(1L), any(ChecklistForm.class));
    }

    @Test
    void 잘못된_입력이면_점검일지를_등록하지_않는다() throws Exception {
        given(facilityService.findFacility(1L)).willReturn(pump());
        mockMvc.perform(post("/inspections/new/1").param("facilityId", "").param("recordedType", "PUMP")
                        .param("results[LEAK]", "ACTION_NEEDED").param("results[DAMAGE]", "OK"))
                .andExpect(view().name("inspections/checklist"))
                .andExpect(model().attributeHasFieldErrors("checklist", "facilityId", "inspectionDate",
                        "inspectorName", "results[COVER]", "reasons[LEAK]"))
                .andExpect(model().attributeHasErrors("checklist"));
        verify(inspectionReportService, never()).registerChecklist(any(), any());
    }

    private MockHttpServletRequestBuilder pumpRequest(String leakResult) {
        return post("/inspections/new/1")
                .param("facilityId", "1").param("recordedType", "PUMP").param("formVersion", "1")
                .param("inspectionDate", "2026-09-10").param("inspectorName", "김점검")
                .param("results[LEAK]", leakResult).param("results[ROTATION]", "UNCHECKED")
                .param("results[COVER]", "OK").param("results[POWER_CONNECTION]", "OK");
    }

    @ParameterizedTest
    @ValueSource(strings = {"results[ALIEN]", "results[DISPLAY]", "reasons[ALIEN]", "results[LEAK]"})
    void 다른_항목과_중복_답변을_거절한다(String field) throws Exception {
        given(facilityService.findFacility(1L)).willReturn(pump());
        mockMvc.perform(pumpRequest("OK").param(field, "OK"))
                .andExpect(view().name("inspections/checklist"))
                .andExpect(model().attributeHasErrors("checklist"));
        verifyNoInteractions(inspectionReportService);
    }

    @Test
    void 작성_중_시설_유형이_바뀌면_제출한_펌프_양식과_값을_다시_보여준다() throws Exception {
        Facility changed = new Facility("시설", FacilityType.PIPE, "부산");
        ReflectionTestUtils.setField(changed, "id", 1L);
        given(facilityService.findFacility(1L)).willReturn(changed);
        mockMvc.perform(pumpRequest("ACTION_NEEDED").param("reasons[LEAK]", "접합부에 물방울"))
                .andExpect(model().attributeHasErrors("checklist"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("보호커버 상태")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("접합부에 물방울")));
        verifyNoInteractions(inspectionReportService);
    }

    @Test
    void 결과_코드와_사유_길이를_서버에서_검사한다() throws Exception {
        given(facilityService.findFacility(1L)).willReturn(pump());
        mockMvc.perform(pumpRequest("NORMAL").param("reasons[COVER]", "가".repeat(501)))
                .andExpect(model().attributeHasFieldErrors("checklist", "results[LEAK]", "reasons[COVER]"));
        verifyNoInteractions(inspectionReportService);
    }

    @Test
    void 분석_실패는_한번만_보이며_새로고침하면_다시_분석전이다() throws Exception {
        given(aiAnalysisService.analyzeInspection(1L)).willThrow(new OpenAiAnalysisException("테스트 오류"));
        given(inspectionReportService.findDetail(1L))
                .willReturn(new InspectionReportService.Detail(new InspectionReport(pump(), "누수 관찰", LocalDate.now()), null));
        var session = new MockHttpSession();
        mockMvc.perform(post("/inspections/1/analysis").session(session))
                .andExpect(redirectedUrl("/inspections/1"));
        mockMvc.perform(get("/inspections/1").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI 분석 실패 · 다시 시도해 주세요")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("검토 결과 확정"))));
        mockMvc.perform(get("/inspections/1").session(session))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("AI 분석 전")));
    }

    private Facility pump() {
        Facility facility = new Facility("펌프", FacilityType.PUMP, "부산");
        ReflectionTestUtils.setField(facility, "id", 1L);
        return facility;
    }

    @Test
    void 점검일지_상세를_조회한다() throws Exception {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 정수장"
        );

        InspectionReport inspection = new InspectionReport(
                facility,
                "평소보다 진동이 큼",
                LocalDate.of(2026, 9, 10)
        );

        given(inspectionReportService.findDetail(1L))
                .willReturn(new InspectionReportService.Detail(inspection, null));

        mockMvc.perform(get("/inspections/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspections/detail"))
                .andExpect(model().attributeExists("inspection"));

        verify(inspectionReportService).findDetail(1L);
    }

    @Test
    void 점검일지_AI분석_요청() throws Exception {
        mockMvc.perform(post("/inspections/1/analysis"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/1"));

        verify(aiAnalysisService).analyzeInspection(1L);
    }

    @Test
    void 점검일지_처리상태를_변경한다() throws Exception {
        mockMvc.perform(post("/inspections/1/status")
                        .param("processingStatus", "IN_PROGRESS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/1"));

        verify(inspectionReportService)
                .changeProcessingStatus(
                        1L,
                        ProcessingStatus.IN_PROGRESS
                );
    }
}