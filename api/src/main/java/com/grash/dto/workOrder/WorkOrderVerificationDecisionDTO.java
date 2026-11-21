package com.grash.dto.workOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Supervisor decision on a work order awaiting verification")
public class WorkOrderVerificationDecisionDTO {

    @Size(max = 2000)
    @Schema(description = "Mandatory rejection reason. Only used when rejecting.")
    private String reason;

    @Size(max = 4000)
    @Schema(description = "Optional supervisor comments stored with the decision")
    private String comments;
}
