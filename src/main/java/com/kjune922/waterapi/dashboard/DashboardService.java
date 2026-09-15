package com.kjune922.waterapi.dashboard;

import com.kjune922.waterapi.analysis.AiAnalysisRepository;
import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.facility.FacilityRepository;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import com.kjune922.waterapi.inspection.ProcessingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final FacilityRepository facilityRepository;
    private final InspectionReportRepository inspectionReportRepository;
    private final AiAnalysisRepository aiAnalysisRepository;

    public DashboardSummary getSummary() {
        return new DashboardSummary(
                facilityRepository.count(),
                inspectionReportRepository.count(),

                inspectionReportRepository.countByProcessingStatus(
                        ProcessingStatus.PENDING
                ),
                inspectionReportRepository.countByProcessingStatus(
                        ProcessingStatus.IN_PROGRESS
                ),
                inspectionReportRepository.countByProcessingStatus(
                        ProcessingStatus.COMPLETED
                ),

                aiAnalysisRepository.count(),

                aiAnalysisRepository.countByRiskLevel(
                        RiskLevel.NORMAL
                ),
                aiAnalysisRepository.countByRiskLevel(
                        RiskLevel.CAUTION
                ),
                aiAnalysisRepository.countByRiskLevel(
                        RiskLevel.WARNING
                )
        );
    }
}
