package com.grash.model.enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ten-state work order lifecycle introduced in Phase 3.
 * <p>
 * The lifecycle state is stored as a STRING ({@code lifecycle_state} column) so that future states can be
 * appended without renumbering existing data. The legacy {@link Status} enum (stored as an ordinal) is kept
 * fully intact for backward compatibility and is always kept in sync by a single mapping table.
 */
@Schema(description = "Lifecycle state of a work order")
public enum WorkOrderLifecycleState {

    REPORTED,
    TRIAGED,
    ASSIGNED,
    ACKNOWLEDGED,
    IN_PROGRESS,
    WAITING_ON_HOLD,
    WORK_COMPLETED,
    VERIFICATION_PENDING,
    VERIFIED,
    CLOSED;

    /**
     * Runtime mapping from the detailed lifecycle state to the legacy four-value Status.
     * REPORTED/TRIAGED/ASSIGNED/ACKNOWLEDGED are all still "OPEN" for legacy consumers.
     */
    public Status toLegacyStatus() {
        switch (this) {
            case IN_PROGRESS:
                return Status.IN_PROGRESS;
            case WAITING_ON_HOLD:
                return Status.ON_HOLD;
            case WORK_COMPLETED:
            case VERIFICATION_PENDING:
            case VERIFIED:
            case CLOSED:
                return Status.COMPLETE;
            case REPORTED:
            case TRIAGED:
            case ASSIGNED:
            case ACKNOWLEDGED:
            default:
                return Status.OPEN;
        }
    }

    /**
     * Mapping used when reading legacy records that only have a Status value.
     * COMPLETE maps to CLOSED because, historically, COMPLETE was the terminal state.
     */
    public static WorkOrderLifecycleState fromLegacyStatus(Status status) {
        if (status == null) return REPORTED;
        switch (status) {
            case IN_PROGRESS:
                return IN_PROGRESS;
            case ON_HOLD:
                return WAITING_ON_HOLD;
            case COMPLETE:
                return CLOSED;
            case OPEN:
            default:
                return REPORTED;
        }
    }

    /**
     * True once a technician has actually started executing the work.
     * Used to decide whether cancellation needs supervisory authorization.
     */
    public boolean isWorkStarted() {
        return this == IN_PROGRESS || this == WAITING_ON_HOLD || this == WORK_COMPLETED
                || this == VERIFICATION_PENDING || this == VERIFIED;
    }

    /**
     * True when this state is after the technician handed the work over for verification.
     */
    public boolean isAfterTechnicianCompletion() {
        return this == WORK_COMPLETED || this == VERIFICATION_PENDING || this == VERIFIED || this == CLOSED;
    }
}
