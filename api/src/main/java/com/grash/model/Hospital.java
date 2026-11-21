package com.grash.model;

import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A physical hospital inside a company tenant. One company can operate several
 * hospitals; locations, departments and assets are linked to them.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "hospital", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hospital_company_code", columnNames = {"company_id", "code"})
})
@Schema(description = "Hospital entity, the top level of the facility hierarchy")
public class Hospital extends CompanyAudit {

    @Schema(description = "Hospital name", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Size(max = 255)
    private String name;

    @Schema(description = "Short code used on labels and reports")
    @Size(max = 64)
    @Column(name = "code")
    private String code;

    @Schema(description = "Street address")
    @Size(max = 1000)
    private String address;

    @Schema(description = "Main phone number")
    @Size(max = 64)
    private String phone;

    @Schema(description = "Contact email")
    @Size(max = 255)
    private String email;

    @Schema(description = "IANA timezone, for example Europe/Paris")
    @Size(max = 64)
    private String timezone;

    @Schema(description = "Free text description")
    @Size(max = 5000)
    private String description;

    @Schema(description = "Whether the hospital is active")
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
