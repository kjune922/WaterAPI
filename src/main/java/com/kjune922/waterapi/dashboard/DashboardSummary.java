package com.kjune922.waterapi.dashboard;

public record DashboardSummary(
        long facilityCount,
        long inspectionCount,
        long pendingCount,
        long inProgressCount,
        long completedCount,
        long analysisCount,
        long normalCount,
        long cautionCount,
        long warningCount
) {
}
