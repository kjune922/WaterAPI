package com.kjune922.waterapi.facility;

import com.kjune922.waterapi.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;

    // 시설 생성 및 저장
    @Transactional
    public Facility registerFacility(
            String name, FacilityType facilityType, String location
    ) {
        Facility facility = new Facility(name, facilityType, location);
        return facilityRepository.save(facility);
    }

    // 모든 시설 조회
    public List<Facility> findFacilities() {
        return facilityRepository.findAll();
    }

    // 시설 id로 조회
    public Facility findFacility(Long id){
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("시설을 찾을 수 없습니다."));
    }
}
