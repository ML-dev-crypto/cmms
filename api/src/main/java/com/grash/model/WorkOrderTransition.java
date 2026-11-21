package com.grash.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

import jakarta.validation.constraints.NotNull;

/**
 * Append-only audit trail of work order lifecycle transitions.
 * <p>
 * Rows are only ever inserted by {@code WorkOrderLifecycleService#recordTransition}. No update or delete
 * operation is exposed through any API and all columns are marked {@code updatable = false}; the only
 * sanctioned removal path is the database retention policy described in the Phase 3 report.
 */
@Entity
@Table(name = "work_order_transition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Immutable record of a single work order lifecycle transition")
public class WorkOrderTransition extends CompanyAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Schema(description = "Unique identifier", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "work_order_id", nullable = false, updatable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Schema(description = "Work order whose lifecycle changed")
    private WorkOrder workOrder;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", nullable = false, updatable = false, length = 32)
    @Schema(description = "State before the transition")
    private WorkOrderLifecycleState previousState;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "new_state", nullable = false, updatable = false, length = 32)
    @Schema(description = "State after the transition")
    private WorkOrderLifecycleState newState;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Schema(description = "User who performed the transition. Set to null only if the user account is " +
            "hard-deleted; the transition itself is retained permanently.")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_code", nullable = false, updatable = false, length = 32)
    @Schema(description = "Machine-readable reason category")
    private WorkOrderTransitionReasonCode reasonCode;

    @Column(name = "reason", length = 2000, updatable = false)
    @Schema(description = "Free-text reason supplied by the actor")
    private String reason;

    @Column(name = "metadata", columnDefinition = "TEXT", updatable = false)
    @Schema(description = "Optional additional JSON metadata")
    private String metadata;
}
