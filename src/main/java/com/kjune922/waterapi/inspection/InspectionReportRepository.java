package com.kjune922.waterapi.inspection;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {

    @Query("select r from InspectionReport r join fetch r.facility left join fetch r.answers where r.id = :id")
    Optional<InspectionReport> findWithAnswers(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InspectionReport r where r.id = :id")
    Optional<InspectionReport> lockById(Long id);

    // 요청 중 먼저 읽어 둔 엔티티 대신, 잠근 행에서 현재 수정 번호를 직접 읽는다.
    @Query(value = "select input_revision from inspection_reports where id = :id for update", nativeQuery = true)
    Optional<Long> lockRevision(Long id);

    // 목록 화면에서 시설명을 사용하므로 페이지와 함께 시설을 가져온다.
    // ManyToOne 단일 연관관계여서 컬렉션 fetch join의 페이지 중복 문제가 없다.
    @Override
    @EntityGraph(attributePaths = "facility")
    Page<InspectionReport> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "facility")
    Page<InspectionReport> findByProcessingStatus(ProcessingStatus processingStatus, Pageable pageable);

    long countByProcessingStatus(ProcessingStatus processingStatus);

    boolean existsByFacility_Id(Long facilityId);
}

