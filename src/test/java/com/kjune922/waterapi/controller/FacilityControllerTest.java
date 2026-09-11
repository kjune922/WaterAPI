package com.kjune922.waterapi.controller;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.service.FacilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;


import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(FacilityController.class)
class FacilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FacilityService facilityService;

    @Test
    void 시설_목록_조회 () throws Exception {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 수영장"
        );

        given(facilityService.findFacilities())
                .willReturn(List.of(facility));

        mockMvc.perform(get("/facilities"))
                .andExpect(status().isOk())
                .andExpect(view().name("facilities/list"))
                .andExpect(model().attributeExists("facilities"));
    }

    @Test
    void 시설_등록_화면_조회 () throws Exception {
        mockMvc.perform(get("/facilities/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("facilities/new"))
                .andExpect(model().attributeExists("facilityForm"))
                .andExpect(model().attributeExists("facilityTypes"));
    }

    @Test
    void 시설을_등록한다() throws Exception {
        mockMvc.perform(post("/facilities")
                        .param("name", "2번 펌프")
                        .param("facilityType", "PUMP")
                        .param("location", "청주 정수장"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/facilities"));

        verify(facilityService).registerFacility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 정수장"
        );
    }

    @Test
    void 잘못된_입력이면_시설을_등록하지_않는다() throws Exception {
        mockMvc.perform(post("/facilities")
                        .param("name", "")
                        .param("facilityType", "")
                        .param("location", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("facilities/new"))
                .andExpect(model().attributeHasFieldErrors(
                        "facilityForm",
                        "name",
                        "facilityType",
                        "location"
                ));

        verifyNoInteractions(facilityService);
    }
}