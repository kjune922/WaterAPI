package com.kjune922.waterapi.inspection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReportRepository extends JpaRepository<InspectionReport, Long> {
    Page<InspectionReport> findByProcessingStatus(ProcessingStatus processingStatus, Pageable pageable);
}

