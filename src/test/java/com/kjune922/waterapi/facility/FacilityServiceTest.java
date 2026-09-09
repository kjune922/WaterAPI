package com.kjune922.waterapi.facility;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class FacilityServiceTest {

    @Autowired
    private FacilityService facilityService;

    @Test
    void 시설_등록() {
        Facility facility = facilityService.registerFacility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 수영장"
        );

        assertThat(facility.getId()).isNotNull();
        assertThat(facility.getName()).isEqualTo("2번 펌프");
        assertThat(facility.getFacilityType()).isEqualTo(FacilityType.PUMP);
        assertThat(facility.getLocation()).isEqualTo("청주 수영장");
    }

    @Test
    void 존재하지않는_시설은_조회불가() {
        assertThatThrownBy(() -> facilityService.findFacility(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("시설을 찾을 수 없습니다.");
    }
}