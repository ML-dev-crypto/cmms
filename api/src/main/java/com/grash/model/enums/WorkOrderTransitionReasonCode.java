package com.grash.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Machine-readable reason categories for a lifecycle transition. A free-text reason is additionally required
 * for every controlled transition (returns, reassignment, rework, resume, reopen and cancellation).
 */
@Schema(description = "Reason category of a work order lifecycle transition")
public enum WorkOrderTransitionReasonCode {
    /** Regular forward movement through the workflow. */
    NORMAL,
    /** Cancellation: recorded as a transition to CLOSED with this reason code. */
    CANCELLED,
    /** Reopening of a closed work order. */
    REOPENED,
    /** Work returned for rework from completion/verification. */
    REWORK,
    /** Controlled reassignment back to triage/assignment. */
    REASSIGNMENT,
    /** Work order returned to REPORTED because it was not actionable. */
    RETURNED,
    /** Resuming work that was waiting on hold. */
    RESUMED,
    /** Synthetic reason used by the legacy status adapter, never accepted from the lifecycle API. */
    LEGACY_STATUS_CHANGE
}
