package com.grash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DepartmentMiniDTO {
    @Schema(description = "Unique identifier")
    private Long id;
    @Schema(description = "Department name")
    private String name;
    @Schema(description = "Department code")
    private String code;
    @Schema(description = "Identifier of the parent hospital")
    private Long hospitalId;
}
