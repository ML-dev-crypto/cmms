package com.grash.dto.workOrder;

import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Describes one lifecycle action that the requesting user may currently perform.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkOrderAvailableTransitionDTO {
    private WorkOrderLifecycleState targetState;
    private boolean reasonRequired;
    private List<WorkOrderTransitionReasonCode> allowedReasonCodes;
    private boolean supervisory;
}
