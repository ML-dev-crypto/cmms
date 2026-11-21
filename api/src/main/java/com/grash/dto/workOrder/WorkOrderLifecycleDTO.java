package com.grash.dto.workOrder;

import com.grash.model.enums.Status;
import com.grash.model.enums.WorkOrderLifecycleState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Current lifecycle state and the transitions the requesting user may perform")
public class WorkOrderLifecycleDTO {
    private WorkOrderLifecycleState currentState;
    private Status status;
    private List<WorkOrderAvailableTransitionDTO> availableTransitions;
}
