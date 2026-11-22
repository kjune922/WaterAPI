package com.kjune922.waterapi.inspection;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InspectionAnswer {
    @Column(nullable = false, length = 40)
    private String code;
    @Column(nullable = false, length = 80)
    private String label;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24)
    private ObservationResult result;
    @Column(length = 500)
    private String reason;

    public InspectionAnswer(ChecklistItem item, ObservationResult result, String reason) {
        this.code = item.code();
        this.label = item.label();
        this.result = result;
        this.reason = reason;
    }
}
