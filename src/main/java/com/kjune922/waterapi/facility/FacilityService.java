package com.kjune922.waterapi.facility;

import com.kjune922.waterapi.exception.FacilityInUseException;
import com.kjune922.waterapi.exception.ResourceNotFoundException;
import com.kjune922.waterapi.inspection.InspectionReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final InspectionReportRepository inspectionReportRepository;

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

    // 시설 id로 조회 (단건 조회)
    public Facility findFacility(Long id){
        return facilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("시설을 찾을 수 없습니다."));
    }

    /**
     * 등록은 새 엔티티를 생성해 저장
     * 수정은 기존 엔티티 조회해서 값을 변경
     * 삭제는 점검일지 여부 확인한 뒤 명시적으로 삭제
     */

    // 시설 수정 메소드 추가
    @Transactional
    public void updateFacility(Long facilityId, String name, FacilityType facilityType, String location){
        Facility facility = findFacility(facilityId); // 기존 시설을 조회해 영속성 컨텍스트에 등록
        facility.update(name, facilityType, location); // 변경감지로 DB에 수정내용 반영
    }

    // 시설 삭제 메소드 추가
    @Transactional
    public void deleteFacility(Long facilityId){
        Facility facility = findFacility(facilityId);

        if(inspectionReportRepository.existsByFacility_Id(facilityId)) {
            throw new FacilityInUseException("점검일지가 등록된 시설은 삭제할 수 없습니다.");
        }

        facilityRepository.delete(facility);
    }
}
