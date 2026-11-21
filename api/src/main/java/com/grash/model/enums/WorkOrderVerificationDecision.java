package com.grash.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Verification decision recorded for a work order.
 * <p>
 * Records are append-only: a PENDING row documents a submission/assignment, and every
 * APPROVED or REJECTED row permanently documents a supervisor decision. Rejections are never
 * overwritten when the technician resubmits work.
 */
@Schema(description = "Supervisor verification decision")
public enum WorkOrderVerificationDecision {
    PENDING,
    APPROVED,
    REJECTED
}
