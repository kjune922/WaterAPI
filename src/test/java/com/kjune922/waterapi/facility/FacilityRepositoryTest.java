package com.kjune922.waterapi.facility;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class FacilityRepositoryTest {

    @Autowired
    private FacilityRepository facilityRepository;

    @Test
    void 시설을_저장() {
        Facility facility = new Facility(
                "2번 펌프",
                FacilityType.PUMP,
                "청주 수영장"
        );

        Facility savedFacility = facilityRepository.save(facility);

        assertThat(savedFacility.getId()).isNotNull();
        assertThat(savedFacility.getName()).isEqualTo("2번 펌프");
        assertThat(savedFacility.getFacilityType()).isEqualTo(FacilityType.PUMP);
        assertThat(savedFacility.getLocation()).isEqualTo("청주 수영장");

    }

}