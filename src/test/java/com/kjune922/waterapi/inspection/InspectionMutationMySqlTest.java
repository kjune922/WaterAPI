package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.analysis.AiAnalysis;
import com.kjune922.waterapi.analysis.AiAnalysisConfirmForm;
import com.kjune922.waterapi.analysis.AiAnalysisService;
import com.kjune922.waterapi.analysis.AnalysisReviewStatus;
import com.kjune922.waterapi.exception.AlreadyConfirmedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 테스트 전체를 트랜잭션으로 감싸지 않고 서비스의 실제 커밋/롤백 이후 JDBC로 확인한다. */
@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"mysql-test", "stub"})
class InspectionMutationMySqlTest {
    @Autowired InspectionReportService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired AiAnalysisService analyses;

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
