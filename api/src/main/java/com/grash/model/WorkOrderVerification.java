package com.grash.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.grash.model.abstracts.CompanyAudit;
import com.grash.model.enums.WorkOrderVerificationDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Date;

/**
 * Append-only supervisor verification record for a work order.
 * <p>
 * A row is inserted either when work is submitted for verification / a verifier is assigned
 * ({@link WorkOrderVerificationDecision#PENDING}) or when a supervisor decides
 * ({@link WorkOrderVerificationDecision#APPROVED} / {@link WorkOrderVerificationDecision#REJECTED}).
 * Rows are never updated or deleted through any API, so earlier rejection records survive rework
 * and resubmission cycles.
 */
@Entity
@Table(name = "work_order_verification",
        indexes = {
                @Index(name = "idx_work_order_verification_work_order", columnList = "work_order_id"),
                @Index(name = "idx_work_order_verification_company", columnList = "company_id"),
                @Index(name = "idx_work_order_verification_verifier", columnList = "verifier_id"),
                @Index(name = "idx_work_order_verification_company_decision", columnList = "company_id, decision")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Immutable supervisor verification record")
public class WorkOrderVerification extends CompanyAudit {

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
    @Schema(description = "Work order being verified")
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_id", updatable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Schema(description = "Assigned verifier or supervisor who decided. Null for an unassigned submission.")
    private User verifier;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, updatable = false, length = 16)
    @Schema(description = "Verification decision")
    private WorkOrderVerificationDecision decision;

    @Column(name = "decided_at", updatable = false)
    @Schema(description = "Timestamp of the decision. Null while the record is PENDING.")
    private Date decidedAt;

    @Column(name = "rejection_reason", length = 2000, updatable = false)
    @Schema(description = "Mandatory reason supplied when work is rejected")
    private String rejectionReason;

    @Column(name = "comments", length = 4000, updatable = false)
    @Schema(description = "Optional supervisor comments")
    private String comments;

    @Column(name = "evidence_snapshot", columnDefinition = "TEXT", updatable = false)
    @Schema(description = "JSON snapshot of completion, checklist, labor and part evidence reviewed")
    private String evidenceSnapshot;
}
