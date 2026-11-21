package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for partially updating a hospital")
public class HospitalPatchDTO {
    private String name;
    private String code;
    private String address;
    private String phone;
    private String email;
    private String timezone;
    private String description;
    private Boolean active;
}
