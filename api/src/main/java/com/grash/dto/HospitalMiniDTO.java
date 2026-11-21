package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class HospitalMiniDTO {
    @Schema(description = "Unique identifier")
    private Long id;
    @Schema(description = "Hospital name")
    private String name;
    @Schema(description = "Hospital code")
    private String code;
}
