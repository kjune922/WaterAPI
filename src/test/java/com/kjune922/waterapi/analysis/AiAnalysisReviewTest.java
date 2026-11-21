package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityRepository;
import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.inspection.InspectionReport;
import com.kjune922.waterapi.inspection.InspectionReportService;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiAnalysisReviewTest {
    @Autowired MockMvc mvc;
    @Autowired FacilityRepository facilities;
    @Autowired InspectionReportService inspections;
    @Autowired AiAnalysisService analyses;

    private InspectionReport inspection;
    private AiAnalysis original;

    @BeforeEach
    void analyzeWithStub() {
        Facility facility = facilities.save(new Facility("펌프", FacilityType.PUMP, "부산"));
        inspection = inspections.registerInspection(facility.getId(), "점검 중 진동 관찰", LocalDate.now());
        original = analyses.analyzeInspection(inspection.getId());
    }

    @Test
    void 최초_확정본과_AI_원본을_같은_트랜잭션에서_조회한다() throws Exception {
        String detail = "/inspections/" + inspection.getId();
        mvc.perform(get(detail))
                .andExpect(content().string(containsString(original.getSummary())))
                .andExpect(content().string(containsString("검토 결과 확정")));

        mvc.perform(confirmRequest().param("summary", "현장 재확인").param("riskLevel", "WARNING")
                        .param("abnormalityType", "누수").param("recommendedAction", "밸브 점검"))
                .andExpect(redirectedUrl(detail));

        AiAnalysis confirmed = analyses.findAnalysis(inspection.getId()).orElseThrow();
        assertThat(confirmed.getReviewStatus()).isEqualTo(AnalysisReviewStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(confirmed.getConfirmedSummary()).isEqualTo("현장 재확인");
        assertThat(confirmed.getConfirmedRiskLevel()).isEqualTo(RiskLevel.WARNING);
        assertThat(confirmed.getConfirmedAbnormalityType()).isEqualTo("누수");
        assertThat(confirmed.getConfirmedRecommendedAction()).isEqualTo("밸브 점검");
        assertThat(confirmed.getSummary()).isEqualTo(original.getSummary());
        assertThat(confirmed.getAbnormalityType()).isEqualTo(original.getAbnormalityType());
        assertThat(confirmed.getRecommendedAction()).isEqualTo(original.getRecommendedAction());
        assertThat(confirmed.getRiskLevel()).isEqualTo(original.getRiskLevel());

        mvc.perform(get(detail)).andExpect(content().string(containsString("사용자 확정본")))
                .andExpect(content().string(containsString("현장 재확인")))
                .andExpect(content().string(not(containsString("검토 결과 확정"))));

        mvc.perform(get("/analyses").param("riskLevel", "CAUTION").param("size", "1"))
                .andExpect(content().string(containsString(original.getSummary())));
        mvc.perform(get("/analyses").param("riskLevel", "WARNING"))
                .andExpect(content().string(not(containsString(original.getSummary()))));

        mvc.perform(confirmRequest().param("summary", "두 번째 요청").param("riskLevel", "NORMAL")
                        .param("abnormalityType", "없음").param("recommendedAction", "관찰"))
                .andExpect(status().isConflict());
        AiAnalysis reread = analyses.findAnalysis(inspection.getId()).orElseThrow();
        assertThat(reread.getConfirmedSummary()).isEqualTo(confirmed.getConfirmedSummary());
        assertThat(reread.getConfirmedAt()).isEqualTo(confirmed.getConfirmedAt());
    }

    @Test
    void 잘못된_입력은_필드_오류로_표시하고_확정하지_않는다() throws Exception {
        mvc.perform(confirmRequest().param("summary", " ").param("abnormalityType", "가".repeat(101))
                        .param("riskLevel", "OTHER").param("recommendedAction", "가".repeat(1001)))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("confirmForm", "summary", "abnormalityType",
                        "riskLevel", "recommendedAction"))
                .andExpect(view().name("inspections/detail"));
        assertThat(analyses.findAnalysis(inspection.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(AnalysisReviewStatus.PENDING_REVIEW);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "INVALID")
    void 위험도_오류에서_다른_필드만_수정해도_확정되지_않는다(String invalidRisk) throws Exception {
        mvc.perform(get("/inspections/{id}", inspection.getId()))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<option(?=[^>]*value=\"CAUTION\")(?=[^>]*selected)[^>]*>.*")));

        MockHttpServletRequestBuilder invalid = confirmRequest().param("summary", "입력 유지")
                .param("abnormalityType", "진동").param("recommendedAction", "");
        if (invalidRisk != null) {
            invalid.param("riskLevel", invalidRisk);
        }
        mvc.perform(invalid)
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("confirmForm", "riskLevel", "recommendedAction"))
                .andExpect(content().string(containsString("입력 유지")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<select[^>]*id=\"riskLevel\"[^>]*>\\s*<option[^>]*value=\"\"[^>]*>위험도 선택</option>.*")))
                .andExpect(content().string(not(matchesPattern(
                        "(?s).*<option(?=[^>]*value=\"(?:NORMAL|CAUTION|WARNING)\")(?=[^>]*selected)[^>]*>.*"))));

        mvc.perform(confirmRequest().param("summary", "입력 유지").param("riskLevel", "")
                        .param("abnormalityType", "진동").param("recommendedAction", "재점검"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attributeHasFieldErrors("confirmForm", "riskLevel"));
        assertThat(analyses.findAnalysis(inspection.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(AnalysisReviewStatus.PENDING_REVIEW);
    }

    @Test
    void 재분석_후_오래된_폼은_새_분석을_확정할_수_없다() throws Exception {
        inspections.updateInspection(inspection.getId(), "배관 누수 확인", LocalDate.now());
        AiAnalysis replacement = analyses.analyzeInspection(inspection.getId());
        mvc.perform(confirmRequest().param("summary", "예전 결과").param("riskLevel", "NORMAL")
                        .param("abnormalityType", "없음").param("recommendedAction", "관찰"))
                .andExpect(status().isNotFound());
        assertThat(analyses.findAnalysis(inspection.getId()).orElseThrow().getId()).isEqualTo(replacement.getId());
        assertThat(replacement.getReviewStatus()).isEqualTo(AnalysisReviewStatus.PENDING_REVIEW);
    }

    @Test
    void 다른_점검일지_ID로_확정할_수_없다() throws Exception {
        mvc.perform(post("/inspections/{id}/analysis/{analysisId}/confirm", inspection.getId() + 1, original.getId())
                        .param("summary", "요약").param("riskLevel", "NORMAL")
                        .param("abnormalityType", "없음").param("recommendedAction", "관찰"))
                .andExpect(status().isNotFound());
        assertThat(analyses.findAnalysis(inspection.getId()).orElseThrow().getReviewStatus())
                .isEqualTo(AnalysisReviewStatus.PENDING_REVIEW);
    }

    @Test
    void 서비스_직접_호출도_입력값을_검증한다() {
        AiAnalysisConfirmForm form = AiAnalysisConfirmForm.from(original);
        form.setSummary("가".repeat(501));
        assertThatThrownBy(() -> analyses.confirm(inspection.getId(), original.getId(), form))
                .isInstanceOf(ConstraintViolationException.class);
    }

    private MockHttpServletRequestBuilder confirmRequest() {
        return post("/inspections/{id}/analysis/{analysisId}/confirm", inspection.getId(), original.getId());
    }
}
