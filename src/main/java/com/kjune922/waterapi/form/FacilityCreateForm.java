package com.kjune922.waterapi.form;

import com.kjune922.waterapi.facility.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FacilityCreateForm {

    @NotBlank(message = "시설 이름을 입력해주세요.")
    @Size(max = 50, message = "시설 이름은 50자 이하여야 합니다.")
    private String name;

    @NotNull(message = "시설 유형을 선택해주세요.")
    private FacilityType facilityType;

    @NotBlank(message = "시설 위치를 입력해주세요.")
    @Size(max = 100, message = "시설 위치는 100자 이하여야 합니다.")
    private String location;
}
