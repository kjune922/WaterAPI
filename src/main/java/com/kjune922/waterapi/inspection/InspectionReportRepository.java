package com.kjune922.waterapi.inspection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {

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

