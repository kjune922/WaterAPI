package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.service.FacilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.BDDMockito.given;

@WebMvcTest(InspectionReportController.class)
class InspectionReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionReportService inspectionReportService;

    @MockitoBean
    private FacilityService facilityService;

    @Test
    void 점검일지_목록_조회 () throws Exception {
        given(inspectionReportService.findInspections())
                .willReturn(List.of());

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
                .andExpect(model().attributeExists("inspectionForm"))
                .andExpect(model().attributeExists("facilities"));
    }

    @Test
    void 점검일지를_등록한다() throws Exception {
        mockMvc.perform(post("/inspections")
                        .param("facilityId", "1")
                        .param("content", "평소보다 진동이 큼")
                        .param("inspectionDate", "2026-09-10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections"));

        verify(inspectionReportService).registerInspection(
                1L,
                "평소보다 진동이 큼",
                LocalDate.of(2026, 9, 10)
        );
    }

    @Test
    void 잘못된_입력이면_점검일지를_등록하지_않는다()
            throws Exception {

        given(facilityService.findFacilities())
                .willReturn(List.of());

        mockMvc.perform(post("/inspections")
                        .param("facilityId", "")
                        .param("content", "")
                        .param("inspectionDate", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("inspections/new"))
                .andExpect(model().attributeHasFieldErrors(
                        "inspectionForm",
                        "facilityId",
                        "content",
                        "inspectionDate"
                ))
                .andExpect(model().attributeExists("facilities"));

        verifyNoInteractions(inspectionReportService);
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

        given(inspectionReportService.findInspection(1L))
                .willReturn(inspection);

        mockMvc.perform(get("/inspections/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspections/detail"))
                .andExpect(model().attributeExists("inspection"));

        verify(inspectionReportService).findInspection(1L);
    }
}