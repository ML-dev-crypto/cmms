package com.grash.dto.workOrder;

import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Request to move a work order to another lifecycle state")
public class WorkOrderTransitionRequestDTO {

    @NotNull
    @Schema(description = "Lifecycle state to move to", requiredMode = Schema.RequiredMode.REQUIRED)
    private WorkOrderLifecycleState targetState;

    @Schema(description = "Machine-readable reason category. Defaults to NORMAL when omitted for plain " +
            "forward transitions.")
    private WorkOrderTransitionReasonCode reasonCode;

    @Size(max = 2000)
    @Schema(description = "Free-text reason. Mandatory for controlled transitions (return, reassignment, " +
            "rework, resume, reopen, cancellation).")
    private String reason;

    @Size(max = 10000)
    @Schema(description = "Optional additional JSON metadata stored with the transition")
    private String metadata;

    @Schema(description = "Signature captured when the transition is a technician completion")
    private String signature;

    @Schema(description = "Feedback captured when the transition is a technician completion")
    private String feedback;
}
