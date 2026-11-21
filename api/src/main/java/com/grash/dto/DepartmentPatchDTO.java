package com.grash.dto;

import com.grash.model.Department;
import com.grash.model.Hospital;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "DTO for partially updating a department")
public class DepartmentPatchDTO {
    private String name;
    private String code;
    private String description;
    private Hospital hospital;
    private Department parentDepartment;
    private Boolean active;
}
