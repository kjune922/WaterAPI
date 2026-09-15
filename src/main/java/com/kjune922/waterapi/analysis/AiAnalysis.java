package com.kjune922.waterapi.analysis;

import com.kjune922.waterapi.domain.RiskLevel;
import com.kjune922.waterapi.inspection.InspectionReport;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "ai_analysis")
public class AiAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "inspection_report_id", nullable = false, unique = true)
    private InspectionReport inspectionReport;

    @Column(nullable = false, length = 500)
    private String summary;

    @Column(nullable = false, length = 100)
    private String abnormalityType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskLevel riskLevel;

    @Column(nullable = false, length = 1000)
    private String recommendedAction;

    @Column(nullable = false)
    private LocalDateTime analyzedAt;

    public AiAnalysis(InspectionReport inspectionReport, String summary, String abnormalityType, RiskLevel riskLevel, String recommendedAction, LocalDateTime analyzedAt) {
        this.inspectionReport = inspectionReport;
        this.summary = summary;
        this.abnormalityType = abnormalityType;
        this.riskLevel = riskLevel;
        this.recommendedAction = recommendedAction;
        this.analyzedAt = analyzedAt;
    }
}
