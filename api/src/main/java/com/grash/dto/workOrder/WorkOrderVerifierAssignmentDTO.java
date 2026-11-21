package com.grash.dto.workOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Assignment of a verifier to a completed work order")
public class WorkOrderVerifierAssignmentDTO {

    @Schema(description = "Identifier of the supervisor to assign. Null clears the assignment.")
    private Long verifierId;
}
