package com.kjune922.waterapi.inspection;

import com.kjune922.waterapi.facility.FacilityType;
import java.util.List;

public record ChecklistItem(String code, String label) {
    // 과거 점검표는 작성 당시 저장한 항목과 순서로 다시 연다.
    public static final int VERSION = 1;

    public static List<ChecklistItem> forType(FacilityType type) {
        var leak = new ChecklistItem("LEAK", "누수");
        return switch (type) {
            case PUMP -> List.of(leak, new ChecklistItem("POWER_CONNECTION", "전원 연결"),
                    new ChecklistItem("ROTATION", "회전"), new ChecklistItem("COVER", "보호커버 상태"));
            case PIPE -> List.of(new ChecklistItem("CRACK", "균열·파손"), leak,
                    new ChecklistItem("NOISE", "소음·진동 상태"));
            case VALVE -> List.of(new ChecklistItem("DAMAGE", "외관 손상"), leak,
                    new ChecklistItem("OPERATION", "개폐 작동 상태"));
            case WATER_QUALITY_SENSOR -> List.of(new ChecklistItem("CONTAMINATION", "외관·오염"),
                    new ChecklistItem("POWER", "전원"), new ChecklistItem("DISPLAY", "측정값 표시 상태"));
        };
    }
}
