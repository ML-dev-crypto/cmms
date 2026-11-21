package com.grash.dto.workOrder;

import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;

/**
 * Immutable, already-validated description of a transition. Produced by
 * {@code WorkOrderLifecycleService.validateTransition} and consumed by the apply/record steps.
 */
public record WorkOrderTransitionPlan(
        WorkOrderLifecycleState previousState,
        WorkOrderLifecycleState targetState,
        WorkOrderTransitionReasonCode reasonCode,
        String reason,
        boolean supervisory
) {
}
