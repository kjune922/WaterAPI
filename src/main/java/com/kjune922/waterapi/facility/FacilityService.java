package com.kjune922.waterapi.facility;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;

    @Transactional
    public Facility registerFacility(
            String name, FacilityType facilityType, String location
    ) {
        Facility facility = new Facility(name, facilityType, location);
        return facilityRepository.save(facility);
    }

    public List<Facility> findFacilities() {
        return facilityRepository.findAll();
    }

    public Facility findFacility(Long id){
        return facilityRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("시설을 찾을 수 없습니다."));
    }
}
