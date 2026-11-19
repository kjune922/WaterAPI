package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.Facility;
import com.kjune922.waterapi.facility.FacilityType;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql")
@SpringBootTest
@ActiveProfiles({"mysql-test", "stub"})
@Transactional
class InspectionQueryMySqlTest {
    @Autowired EntityManager em;
    @Autowired InspectionReportRepository repository;
    private Statistics statistics;

    @BeforeEach
    void seedDistinctFacilities() {
        for (int i = 0; i < 30; i++) {
            Facility facility = new Facility("시설 " + i, FacilityType.PUMP, "부산");
            em.persist(facility);
            InspectionReport report = new InspectionReport(facility, "점검 " + i, LocalDate.of(2026, 11, 18));
            if (i % 2 == 0) report.changeProcessingStatus(ProcessingStatus.COMPLETED);
            em.persist(report);
        }
        em.flush();
        em.clear();
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 같은_페이지와_시설명_조회에서_SQL_수를_비교한다(boolean filtered) {
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "inspectionDate", "id"));
        // 변경 전 목록 조회와 같은 JPQL 대조군: 시설을 fetch하지 않는다.
        Page<InspectionReport> before = baselinePage(filtered, pageable);
        List<String> beforeRows = renderedRows(before);
        long beforeQueries = statistics.getPrepareStatementCount();

        em.clear(); // 1차 캐시의 시설이 개선 결과를 부풀리지 않도록 초기화
        statistics.clear();
        Page<InspectionReport> after = filtered
                ? repository.findByProcessingStatus(ProcessingStatus.PENDING, pageable)
                : repository.findAll(pageable);
        List<String> afterRows = renderedRows(after);
        long afterQueries = statistics.getPrepareStatementCount();

        assertThat(afterRows).containsExactlyElementsOf(beforeRows);
        assertThat(after.getNumberOfElements()).isEqualTo(10);
        assertThat(after.getTotalElements()).isEqualTo(filtered ? 15 : 30);
        assertThat(after.getTotalElements()).isEqualTo(before.getTotalElements());
        assertThat(beforeQueries).isEqualTo(12); // 목록 1 + count 1 + 서로 다른 시설 10
        assertThat(afterQueries).isEqualTo(2);   // 시설 join 목록 1 + count 1
        System.out.printf("QUERY_COMPARISON filtered=%s rows=30 pageSize=10 before=%d after=%d%n",
                filtered, beforeQueries, afterQueries);

        Page<InspectionReport> next = filtered
                ? repository.findByProcessingStatus(ProcessingStatus.PENDING, pageable.next())
                : repository.findAll(pageable.next());
        List<Long> firstIds = ids(after);
        assertThat(ids(next)).doesNotContainAnyElementsOf(firstIds);
        assertThat(next.getNumberOfElements()).isEqualTo(filtered ? 5 : 10);
    }

    private Page<InspectionReport> baselinePage(boolean filtered, Pageable pageable) {
        String condition = filtered ? " where r.processingStatus = :status" : "";
        var query = em.createQuery("select r from InspectionReport r" + condition
                + " order by r.inspectionDate desc, r.id desc", InspectionReport.class);
        var count = em.createQuery("select count(r) from InspectionReport r" + condition, Long.class);
        if (filtered) {
            query.setParameter("status", ProcessingStatus.PENDING);
            count.setParameter("status", ProcessingStatus.PENDING);
        }
        List<InspectionReport> rows = query.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(rows, pageable, count.getSingleResult());
    }

    private List<String> renderedRows(Page<InspectionReport> page) {
        List<String> rows = new ArrayList<>();
        for (InspectionReport report : page) {
            rows.add(report.getId() + ":" + report.getFacility().getName() + ":" + report.getProcessingStatus());
        }
        return rows;
    }

    private List<Long> ids(Page<InspectionReport> page) {
        List<Long> ids = new ArrayList<>();
        for (InspectionReport report : page) ids.add(report.getId());
        return ids;
    }
}
