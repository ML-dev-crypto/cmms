package com.grash.dto.workOrder;

import com.grash.dto.UserMiniDTO;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Immutable record of a work order lifecycle transition")
public class WorkOrderTransitionShowDTO {
    private Long id;
    private Long workOrderId;
    private WorkOrderLifecycleState previousState;
    private WorkOrderLifecycleState newState;
    private UserMiniDTO actor;
    private WorkOrderTransitionReasonCode reasonCode;
    private String reason;
    private String metadata;
    private Date createdAt;
}
