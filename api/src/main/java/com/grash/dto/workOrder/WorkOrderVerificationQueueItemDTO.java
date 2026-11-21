package com.grash.dto.workOrder;

import com.grash.dto.AssetMiniDTO;
import com.grash.dto.LocationMiniDTO;
import com.grash.dto.UserMiniDTO;
import com.grash.model.enums.Priority;
import com.grash.model.enums.Status;
import com.grash.model.enums.WorkOrderLifecycleState;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "Work order awaiting supervisor verification")
public class WorkOrderVerificationQueueItemDTO {

    private Long id;
    private String customId;
    private String title;
    private Priority priority;
    private Status status;
    private WorkOrderLifecycleState lifecycleState;
    private Date completedOn;
    private UserMiniDTO completedBy;
    private UserMiniDTO verifier;
    private AssetMiniDTO asset;
    private LocationMiniDTO location;
    private Date dueDate;
}
