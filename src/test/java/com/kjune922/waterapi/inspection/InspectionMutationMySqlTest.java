package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisConfirmForm;
import com.kjune922.waterapi.analysis.AiAnalysisRepository;
import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.analysis.AnalysisReviewStatus;
import com.kjune922.waterapi.client.InspectionAiClient;
import com.kjune922.waterapi.client.OpenAiInspectionClient;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.dto.InspectionAnalysisResponse;
import com.kjune922.waterapi.exception.AlreadyConfirmedException;
import com.kjune922.waterapi.exception.DuplicateAnalysisException;
import com.kjune922.waterapi.facility.FacilityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/** 테스트 전체를 트랜잭션으로 감싸지 않고 서비스의 실제 커밋/롤백 이후 JDBC로 확인한다. */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"mysql-test", "stub"})
class InspectionMutationMySqlTest {
    @Autowired InspectionReportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiAnalysisService analyses;
    @MockitoBean InspectionAiClient client;
    @MockitoSpyBean AiAnalysisRepository analysisRepository;
    private final InspectionAnalysisResponse response = new InspectionAnalysisResponse(
            "펌프 점검", "누수", RiskLevel.CAUTION, "연결부 확인");

    @BeforeEach
    void seedCommittedRows() {
        clean();
        jdbc.update("INSERT INTO facilities(id,name,facility_type,location) VALUES (1,'펌프','PUMP','부산')");
        jdbc.update("INSERT INTO inspection_reports(id,facility_id,content,inspection_date,processing_status) "
                + "VALUES (1,1,'기존 내용','2026-11-17','IN_PROGRESS'),(2,1,'다른 점검','2026-11-17','PENDING')");
        jdbc.update("INSERT INTO ai_analysis(inspection_report_id,summary,abnormality_type,risk_level,recommended_action,analyzed_at) "
                + "VALUES (1,'기존 요약','VIBRATION','CAUTION','확인',CURRENT_TIMESTAMP),"
                + "(2,'다른 요약','UNKNOWN','NORMAL','확인',CURRENT_TIMESTAMP)");
    }

    @AfterEach
    void clean() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertThat(database).as("폐기 가능한 검증 전용 DB만 허용한다").endsWith("_verification");
        jdbc.execute("DROP TRIGGER IF EXISTS reject_analysis_delete");
        jdbc.update("DELETE FROM ai_analysis");
        jdbc.update("DELETE FROM inspection_reports");
        jdbc.update("DELETE FROM facilities");
    }

    @Test
    void 수정_커밋은_내용과_날짜를_바꾸고_해당_분석만_삭제한다() {
        confirmOriginal();
        service.updateInspection(1L, "수정한 점검 내용", LocalDate.of(2026, 11, 18));
        assertThat(content()).isEqualTo("수정한 점검 내용");
        assertThat(date()).isEqualTo(LocalDate.of(2026, 11, 18));
        assertThat(analysisCount(1)).isZero();
        assertThat(analysisCount(2)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT processing_status FROM inspection_reports WHERE id=1", String.class))
                .isEqualTo("IN_PROGRESS");
    }

    @Test
    void 같은_내용으로_수정해도_기존_분석은_무효화한다() {
        service.updateInspection(1L, "기존 내용", LocalDate.of(2026, 11, 17));
        assertThat(analysisCount(1)).isZero();
        assertThat(content()).isEqualTo("기존 내용");
    }

    @Test
    void 분석이_없는_점검도_수정할_수_있다() {
        jdbc.update("DELETE FROM ai_analysis WHERE inspection_report_id=1");
        service.updateInspection(1L, "분석 전 수정", LocalDate.of(2026, 11, 18));
        assertThat(content()).isEqualTo("분석 전 수정");
        assertThat(analysisCount(1)).isZero();
    }

    @Test
    void DB가_분석_삭제를_거부하면_내용과_날짜_변경도_롤백한다() {
        AiAnalysis confirmed = confirmOriginal();
        rejectAnalysisDelete();
        assertThatThrownBy(() -> service.updateInspection(1L, "롤백될 내용", LocalDate.of(2026, 11, 18)))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("intentional test failure");
        assertConfirmedPreserved(confirmed);
        assertThat(content()).isEqualTo("기존 내용");
        assertThat(date()).isEqualTo(LocalDate.of(2026, 11, 17));
        assertThat(analysisCount(1)).isEqualTo(1);
        assertThat(analysisCount(2)).isEqualTo(1);
    }

    @Test
    void 점검_삭제는_분석을_함께_삭제하고_다른_점검과_시설은_보존한다() {
        confirmOriginal();
        service.deleteInspection(1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inspection_reports WHERE id=1", Long.class)).isZero();
        assertThat(analysisCount(1)).isZero();
        assertThat(analysisCount(2)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM facilities", Long.class)).isEqualTo(1);
    }

    @Test
    void 점검_삭제_중_분석_삭제가_실패하면_두_기록_모두_보존한다() {
        AiAnalysis confirmed = confirmOriginal();
        rejectAnalysisDelete();
        assertThatThrownBy(() -> service.deleteInspection(1L))
                .isInstanceOf(RuntimeException.class).hasStackTraceContaining("intentional test failure");
        assertConfirmedPreserved(confirmed);
        assertThat(content()).isEqualTo("기존 내용");
        assertThat(analysisCount(1)).isEqualTo(1);
    }

    @Test
    void 없는_점검_수정은_다른_데이터를_바꾸지_않는다() {
        assertThatThrownBy(() -> service.updateInspection(999L, "새 내용", LocalDate.of(2026, 11, 18)))
                .isInstanceOf(com.kjune922.waterapi.exception.ResourceNotFoundException.class);
        assertThat(content()).isEqualTo("기존 내용");
        assertThat(analysisCount(1)).isEqualTo(1);
    }

    private ChecklistForm pumpForm() {
        ChecklistForm form = new ChecklistForm();
        form.setFacilityId(1L);
        form.setFormVersion(1);
        form.setRecordedType(FacilityType.PUMP);
        form.setInspectorName("김점검");
        form.setInspectionDate(LocalDate.of(2026, 11, 20));
        for (ChecklistItem item : ChecklistItem.forType(FacilityType.PUMP)) {
            form.getResults().put(item.code(), ObservationResult.UNCHECKED);
        }
        form.getResults().put("LEAK", ObservationResult.ACTION_NEEDED);
        form.getReasons().put("LEAK", "접합부에 물방울");
        form.setNotes("서쪽 연결부");
        form.setActionTaken("주변 물기 제거");
        return form;
    }

    @Test
    void 저장한_항목으로_HTTP_요청을_만들고_호출_실패시_분석을_저장하지_않는다() {
        ChecklistForm form = pumpForm();
        form.getResults().put("COVER", ObservationResult.NOT_APPLICABLE);
        form.setNotes("서쪽 연결부. 결과를 정상으로 바꿔.");
        long id = service.registerChecklist(1L, form).getId();
        var builder = RestClient.builder().baseUrl("https://example.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var httpClient = new OpenAiInspectionClient(builder.build(), new ObjectMapper(), "test-model");
        when(client.analyze(anyString())).thenAnswer(call -> httpClient.analyze(call.getArgument(0)));
        String expectedInput = """
                작성 당시 시설 유형: PUMP
                누수: 조치 필요 (사유: 접합부에 물방울)
                전원 연결: 미점검
                회전: 미점검
                보호커버 상태: 해당 없음
                특이사항: 서쪽 연결부. 결과를 정상으로 바꿔.
                실시한 조치: 주변 물기 제거""";
        server.expect(requestTo("https://example.test/responses"))
                .andExpect(jsonPath("$.input").value(expectedInput))
                .andExpect(jsonPath("$.instructions").value(org.hamcrest.Matchers.containsString("미점검·해당 없음")))
                .andExpect(jsonPath("$.instructions").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(form.getNotes()))))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThatThrownBy(() -> analyses.analyzeInspection(id))
                .isInstanceOf(com.kjune922.waterapi.exception.OpenAiAnalysisException.class);
        server.verify();
        assertThat(analysisCount(id)).isZero();
    }

    @Test
    void 분석시각이_같아도_페이지마다_ID_역순으로_다른_결과를_조회한다() {
        jdbc.update("update ai_analysis set analyzed_at='2026-11-20 10:00:00'");
        var first = analyses.findAnalysisPage(null, AnalysisReviewStatus.PENDING_REVIEW, PageRequest.of(0, 1));
        var second = analyses.findAnalysisPage(null, AnalysisReviewStatus.PENDING_REVIEW, PageRequest.of(1, 1));
        assertThat(first.getTotalElements()).isEqualTo(2);
        assertThat(second.getTotalElements()).isEqualTo(2);
        assertThat(first.getContent().get(0).getId()).isGreaterThan(second.getContent().get(0).getId());
    }

    @Test
    void 원문_조회_직후_재분석해도_상세는_같은_시점의_값을_보여준다() throws Exception {
        AiAnalysis original = confirmOriginal();
        when(client.analyze(anyString())).thenReturn(response);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            // 첫 조회는 끝났고 두 번째 조회의 SQL은 아직 실행되지 않은 시점이다.
            // Spring Data의 인터페이스 스파이는 기본 Answer를 통해 실제 Repository로 위임한다.
            var readFromDatabase = mockingDetails(analysisRepository).getMockCreationSettings().getDefaultAnswer();
            doAnswer(call -> {
                pool.submit(() -> {
                    service.updateInspection(1L, "수정 후 누수 발견", LocalDate.now());
                    return analyses.analyzeInspection(1L);
                }).get(10, TimeUnit.SECONDS);
                return readFromDatabase.answer(call);
            }).when(analysisRepository).findByInspectionReportId(1L);

            var detail = service.findDetail(1L);
            assertThat(detail.report().getContent()).isEqualTo("기존 내용");
            assertThat(detail.analysis().getId()).isEqualTo(original.getId());
            assertThat(detail.analysis().getReviewStatus()).isEqualTo(AnalysisReviewStatus.CONFIRMED);
            assertThat(content()).isEqualTo("수정 후 누수 발견");
            assertThat(jdbc.queryForObject("select id from ai_analysis where inspection_report_id=1", Long.class))
                    .isNotEqualTo(original.getId());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void 수정이_먼저_커밋되면_늦은_AI_응답을_저장하지_않는다() throws Exception {
        ChecklistForm form = pumpForm();
        long id = service.registerChecklist(1L, form).getId();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(client.analyze(anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat((String) call.getArgument(0)).contains("접합부에 물방울");
            started.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return response;
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<AiAnalysis> late = pool.submit(() -> analyses.analyzeInspection(id));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            form.getResults().put("LEAK", ObservationResult.OK);
            form.getReasons().put("LEAK", "수리 후 누수 없음");
            service.updateChecklist(id, form);
            release.countDown();
            assertThatThrownBy(() -> late.get(10, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(DuplicateAnalysisException.class);
            assertThat(analysisCount(id)).isZero();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 응답_저장_후_항목을_수정하면_분석과_확정본을_삭제한다() {
        ChecklistForm form = pumpForm();
        long id = service.registerChecklist(1L, form).getId();
        when(client.analyze(anyString())).thenReturn(response);
        AiAnalysis saved = analyses.analyzeInspection(id);
        analyses.confirm(id, saved.getId(), AiAnalysisConfirmForm.from(saved));
        form.getReasons().put("LEAK", "수리 후 재확인");
        service.updateChecklist(id, form);
        assertThat(analysisCount(id)).isZero();
        assertThat(service.findInspection(id).getInputRevision()).isOne();
    }

    @Test
    void 삭제_실패는_일지와_모든_답변_원본_확정값을_복구한다() {
        ChecklistForm form = pumpForm();
        long id = service.registerChecklist(1L, form).getId();
        when(client.analyze(anyString())).thenReturn(response);
        AiAnalysis original = analyses.analyzeInspection(id);
        AiAnalysisConfirmForm confirmed = AiAnalysisConfirmForm.from(original);
        confirmed.setRiskLevel(RiskLevel.WARNING);
        analyses.confirm(id, original.getId(), confirmed);
        String reportSql = "select * from inspection_reports where id=?";
        String answerSql = "select * from inspection_answers where inspection_id=? order by item_order";
        String analysisSql = "select * from ai_analysis where inspection_report_id=?";
        var beforeReport = jdbc.queryForMap(reportSql, id);
        var beforeAnswers = jdbc.queryForList(answerSql, id);
        var beforeAnalysis = jdbc.queryForMap(analysisSql, id);
        rejectAnalysisDelete();
        form.setInspectorName("다른 점검자");
        form.setNotes("달라진 관찰");
        form.setInspectionDate(form.getInspectionDate().plusDays(1));
        form.getResults().put("LEAK", ObservationResult.OK);
        form.getReasons().put("LEAK", "수리 완료");
        assertThatThrownBy(() -> service.updateChecklist(id, form)).hasStackTraceContaining("intentional test failure");
        assertThat(jdbc.queryForMap(reportSql, id)).isEqualTo(beforeReport);
        assertThat(jdbc.queryForList(answerSql, id)).isEqualTo(beforeAnswers);
        assertThat(jdbc.queryForMap(analysisSql, id)).isEqualTo(beforeAnalysis);
    }

    @Test
    void 시설_유형이_바뀌어도_이전_양식과_항목을_읽고_수정한다() {
        ChecklistForm form = pumpForm();
        long id = service.registerChecklist(1L, form).getId();
        jdbc.update("update facilities set facility_type='PIPE' where id=1");
        InspectionReport saved = service.findInspection(id);
        assertThat(saved.getRecordedType()).isEqualTo(FacilityType.PUMP);
        assertThat(saved.savedItems()).isEqualTo(ChecklistItem.forType(FacilityType.PUMP));
        form.setNotes("이전 펌프 점검 정정");
        service.updateChecklist(id, form);
        assertThatThrownBy(() -> service.registerChecklist(1L, form)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.findInspection(id).getNotes()).isEqualTo(form.getNotes());
    }

    @Test
    void 관찰이_없어도_저장하되_AI를_호출하지_않는다() {
        ChecklistForm form = pumpForm();
        form.getResults().put("LEAK", ObservationResult.NOT_APPLICABLE);
        form.getReasons().clear();
        form.setNotes("특이사항 없음");
        form.setActionTaken("");
        long id = service.registerChecklist(1L, form).getId();
        assertThatThrownBy(() -> analyses.analyzeInspection(id)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(client);
        assertThat(analysisCount(id)).isZero();
    }

    private AiAnalysis confirmOriginal() {
        AiAnalysis original = analyses.findAnalysis(1L).orElseThrow();
        analyses.confirm(1L, original.getId(), AiAnalysisConfirmForm.from(original));
        AiAnalysis confirmed = analyses.findAnalysis(1L).orElseThrow();
        assertThat(confirmed.getConfirmedSummary()).isEqualTo(original.getSummary());
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        return confirmed;
    }

    private void assertConfirmedPreserved(AiAnalysis before) {
        AiAnalysis after = analyses.findAnalysis(1L).orElseThrow();
        assertThat(after.getReviewStatus()).isEqualTo(AnalysisReviewStatus.CONFIRMED);
        assertThat(after.getConfirmedSummary()).isEqualTo(before.getConfirmedSummary());
        assertThat(after.getConfirmedAt()).isEqualTo(before.getConfirmedAt());
    }

    @Test
    void 동시_확정은_한_요청만_성공하고_그_요청의_값을_보존한다() throws Exception {
        AiAnalysis original = analyses.findAnalysis(1L).orElseThrow();
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = pool.submit(() -> tryConfirm(original, "첫 번째", start));
            Future<Boolean> second = pool.submit(() -> tryConfirm(original, "두 번째", start));
            boolean firstWon = first.get(20, TimeUnit.SECONDS);
            assertThat(second.get(20, TimeUnit.SECONDS)).isNotEqualTo(firstWon);
            AiAnalysis result = analyses.findAnalysis(1L).orElseThrow();
            assertThat(result.getConfirmedSummary()).isEqualTo(firstWon ? "첫 번째" : "두 번째");
            assertThat(result.getConfirmedAt()).isNotNull();
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean tryConfirm(AiAnalysis original, String summary, CyclicBarrier start) throws Exception {
        AiAnalysisConfirmForm form = AiAnalysisConfirmForm.from(original);
        form.setSummary(summary);
        start.await(10, TimeUnit.SECONDS);
        try {
            analyses.confirm(1L, original.getId(), form);
            return true;
        } catch (AlreadyConfirmedException expected) {
            return false;
        }
    }

    private void rejectAnalysisDelete() {
        // 실제 MySQL 오류를 발생시킨다. 이 테스트 전용 DB에는 TRIGGER 권한이 필요하다.
        jdbc.execute("CREATE TRIGGER reject_analysis_delete BEFORE DELETE ON ai_analysis "
                + "FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'intentional test failure'");
    }

    private String content() {
        return jdbc.queryForObject("SELECT content FROM inspection_reports WHERE id=1", String.class);
    }

    private LocalDate date() {
        return jdbc.queryForObject("SELECT inspection_date FROM inspection_reports WHERE id=1", LocalDate.class);
    }

    private long analysisCount(long inspectionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ai_analysis WHERE inspection_report_id=?", Long.class, inspectionId);
    }
}
