package com.grash.model;

import com.grash.dto.IdDTO;
import com.grash.model.abstracts.CompanyAudit;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Grants a user access to a part of the facility hierarchy. A user without any
 * scope rows keeps full access to the company (backward compatible). Scope rows
 * are additive: when at least one row exists, the union of all rows defines the
 * accessible hospitals, departments and locations.
 */
@Entity
@Data
@NoArgsConstructor
@Table(name = "user_scope")
@Schema(description = "Scope granting a user access to hospitals, departments or locations")
public class UserScope extends CompanyAudit {

    @Schema(description = "Scoped user", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Schema(description = "Hospital the user can access", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hospital_id")
    private Hospital hospital;

    @Schema(description = "Department the user can access", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Schema(description = "Location the user can access", implementation = IdDTO.class)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "location_id")
    private Location location;

    @Schema(description = "Whether this is the user's primary scope")
    private boolean primaryScope;
}
