package com.grash.dto.workOrder;

import com.grash.dto.TaskShowDTO;
import com.grash.dto.UserMiniDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@Schema(description = "Everything a supervisor needs to verify a completed work order")
public class WorkOrderVerificationDetailDTO {

    @Schema(description = "Work order being verified")
    private WorkOrderShowDTO workOrder;

    @Schema(description = "Assigned verifier, if any")
    private UserMiniDTO verifier;

    @Schema(description = "Checklist results captured by the technician")
    private List<TaskShowDTO> tasks = new ArrayList<>();

    @Schema(description = "Labor records")
    private List<WorkOrderVerificationLaborDTO> labors = new ArrayList<>();

    @Schema(description = "Parts consumed")
    private List<WorkOrderVerificationPartDTO> parts = new ArrayList<>();

    @Schema(description = "Permanent verification history, oldest first")
    private List<WorkOrderVerificationHistoryDTO> history = new ArrayList<>();

    @Schema(description = "Reasons that currently prevent approval")
    private List<String> blockers = new ArrayList<>();

    @Schema(description = "Whether the work order is currently awaiting verification")
    private boolean awaitingVerification;

    @Schema(description = "Whether the requesting user may approve or reject")
    private boolean canDecide;

    @Schema(description = "Whether the requesting user may assign the verifier")
    private boolean canAssignVerifier;
}
