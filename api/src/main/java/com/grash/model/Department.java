package com.grash.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.grash.dto.IdDTO;
import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A department inside a hospital, for example Radiology or Intensive Care.
 * Departments can be nested through {@link #parentDepartment}.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "department", uniqueConstraints = {
        @UniqueConstraint(name = "uk_department_hospital_code", columnNames = {"hospital_id", "code"})
})
@Schema(description = "Department entity, a clinical or support unit of a hospital")
public class Department extends CompanyAudit {

    @Schema(description = "Department name", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Size(max = 255)
    private String name;

    @Schema(description = "Short code used on labels and reports")
    @Size(max = 64)
    private String code;

    @Schema(description = "Free text description")
    @Size(max = 5000)
    private String description;

    @Schema(description = "Hospital the department belongs to", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Hospital hospital;

    @Schema(description = "Optional parent department", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Department parentDepartment;

    @Schema(description = "Whether the department is active")
    private boolean active = true;

    public boolean canBeViewedBy(User user) {
        return hasRoleAccess(user) || user.getRole().getViewPermissions().contains(PermissionEntity.HOSPITALS_AND_DEPARTMENTS);
    }

    public boolean canBeEditedBy(User user) {
        return hasRoleAccess(user) || user.getRole().getCreatePermissions().contains(PermissionEntity.HOSPITALS_AND_DEPARTMENTS)
                || user.getRole().getEditOtherPermissions().contains(PermissionEntity.HOSPITALS_AND_DEPARTMENTS);
    }

    public boolean canBeDeletedBy(User user) {
        return hasRoleAccess(user) || user.getRole().getDeleteOtherPermissions().contains(PermissionEntity.HOSPITALS_AND_DEPARTMENTS);
    }

    private boolean hasRoleAccess(User user) {
        RoleCode code = user.getRole().getCode();
        return RoleCode.ADMIN.equals(code) || RoleCode.LIMITED_ADMIN.equals(code);
    }
}
