package com.kjune922.waterapi.facility;

import org.springframework.data.jpa.repository.JpaRepository;

                                        // JpaRepository<관리할 엔티티 타입, 엔티티의 ID 타입>
public interface FacilityRepository extends JpaRepository<Facility,Long> {
}
