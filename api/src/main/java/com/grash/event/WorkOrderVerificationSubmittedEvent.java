package com.grash.event;

import lombok.Getter;

/**
 * Published inside the lifecycle transaction when a technician moves a work order to
 * {@code VERIFICATION_PENDING}. The verification service listens synchronously so the PENDING
 * verification record is persisted atomically with the lifecycle transition, without creating a
 * dependency cycle between {@code WorkOrderService} and {@code WorkOrderVerificationService}.
 */
@Getter
public class WorkOrderVerificationSubmittedEvent {
    private final Long workOrderId;
    private final Long submitterId;

    public WorkOrderVerificationSubmittedEvent(Long workOrderId, Long submitterId) {
        this.workOrderId = workOrderId;
        this.submitterId = submitterId;
    }
}
