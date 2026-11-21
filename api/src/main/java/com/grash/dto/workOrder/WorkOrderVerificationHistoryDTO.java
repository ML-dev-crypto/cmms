package com.grash.dto.workOrder;

import com.grash.dto.UserMiniDTO;
import com.grash.model.enums.WorkOrderVerificationDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "One record in the permanent verification history")
public class WorkOrderVerificationHistoryDTO {

    @Schema(description = "Verification record identifier")
    private Long id;

    @Schema(description = "Verifier or deciding supervisor")
    private UserMiniDTO verifier;

    @Schema(description = "Decision recorded")
    private WorkOrderVerificationDecision decision;

    @Schema(description = "Time of the decision")
    private Date decidedAt;

    @Schema(description = "Reason supplied for a rejection")
    private String rejectionReason;

    @Schema(description = "Supervisor comments")
    private String comments;

    @Schema(description = "Record creation time")
    private Date createdAt;
}
