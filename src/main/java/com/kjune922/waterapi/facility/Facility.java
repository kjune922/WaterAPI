package com.kjune922.waterapi.facility;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "facilities")
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FacilityType facilityType;

    @Column(nullable = false, length = 100)
    private String location;

    public Facility(String name, FacilityType facilityType, String location) {
        validateName(name);
        validateFacilityType(facilityType);
        validateLocation(location);

        this.name = name;
        this.facilityType = facilityType;
        this.location = location;
    }

    private void validateName(String name){
        if(name == null || name.isBlank()){
            throw new IllegalArgumentException("시설 이름은 비어 있을 수 없습니다.");
        }
    }
    private void validateFacilityType(FacilityType facilityType){
        if(facilityType == null){
            throw new IllegalArgumentException("시설 유형은 필수입니다.");
        }
    }
    private void validateLocation(String location){
        if(location == null || location.isBlank()){
            throw new IllegalArgumentException("시설 위치는 비어 있을 수 없습니다.");
        }
    }
}
