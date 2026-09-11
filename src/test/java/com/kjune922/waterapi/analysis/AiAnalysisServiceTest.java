package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.client.InspectionAiClient;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import com.kjune922.waterapi.inspection.InspectionReport;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

    @Mock
    private InspectionReportRepository inspectionReportRepository;

    @Mock
    private AiAnalysisRepository aiAnalysisRepository;

    @Mock
    private InspectionAiClient inspectionAiClient;

    @InjectMocks
    private AiAnalysisService aiAnalysisService;

    @Test
    void 점검일지를_AI로_분석하고_결과를_저장한다() {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 정수장"
        );

        InspectionReport inspectionReport = new InspectionReport(
                facility,
                "펌프 진동이 크고 압력이 감소했습니다.",
                LocalDate.of(2026, 9, 11)
        );

        InspectionAnalysisResponse response =
                new InspectionAnalysisResponse(
                        "펌프 진동과 압력 저하가 확인됨",
                        "PUMP_ABNORMALITY",
                        RiskLevel.WARNING,
                        "베어링과 흡입구를 점검하세요."
                );

        given(inspectionReportRepository.findById(1L))
                .willReturn(Optional.of(inspectionReport));

        given(aiAnalysisRepository
                .existsByInspectionReportId(1L))
                .willReturn(false);

        given(inspectionAiClient.analyze(
                inspectionReport.getContent()
        )).willReturn(response);

        given(aiAnalysisRepository.save(any(AiAnalysis.class)))
                .willAnswer(invocation ->
                        invocation.getArgument(0));

        AiAnalysis result =
                aiAnalysisService.analyzeInspection(1L);

        assertThat(result.getInspectionReport())
                .isEqualTo(inspectionReport);
        assertThat(result.getSummary())
                .isEqualTo("펌프 진동과 압력 저하가 확인됨");
        assertThat(result.getAbnormalityType())
                .isEqualTo("PUMP_ABNORMALITY");
        assertThat(result.getRiskLevel())
                .isEqualTo(RiskLevel.WARNING);
        assertThat(result.getRecommendedAction())
                .isEqualTo("베어링과 흡입구를 점검하세요.");
        assertThat(result.getAnalyzedAt()).isNotNull();

        verify(inspectionAiClient)
                .analyze(inspectionReport.getContent());
        verify(aiAnalysisRepository)
                .save(any(AiAnalysis.class));
    }

    @Test
    void 이미_분석된_점검일지는_다시_분석할_수_없다() {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 정수장"
        );

        InspectionReport inspectionReport = new InspectionReport(
                facility,
                "펌프 진동이 큽니다.",
                LocalDate.of(2026, 9, 11)
        );

        given(inspectionReportRepository.findById(1L))
                .willReturn(Optional.of(inspectionReport));

        given(aiAnalysisRepository
                .existsByInspectionReportId(1L))
                .willReturn(true);

        assertThatThrownBy(() ->
                aiAnalysisService.analyzeInspection(1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "이미 AI 분석이 완료된 점검일지입니다."
                );

        verifyNoInteractions(inspectionAiClient);
        verify(aiAnalysisRepository, never())
                .save(any(AiAnalysis.class));
    }

    @Test
    void 존재하지_않는_점검일지는_분석할_수_없다() {
        given(inspectionReportRepository.findById(999L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                aiAnalysisService.analyzeInspection(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("점검일지를 찾을 수 없습니다.");

        verifyNoInteractions(inspectionAiClient);
        verifyNoInteractions(aiAnalysisRepository);
    }
}
