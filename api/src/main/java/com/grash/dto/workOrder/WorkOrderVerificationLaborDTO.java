package com.grash.dto.workOrder;

import com.grash.dto.UserMiniDTO;
import com.grash.model.enums.TimeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@NoArgsConstructor
@Schema(description = "Labor record shown to a verifier")
public class WorkOrderVerificationLaborDTO {

    private Long id;
    private UserMiniDTO assignedTo;
    private long duration;
    private long hourlyRate;
    private Date startedAt;
    private TimeStatus status;
    private String timeCategory;
}
