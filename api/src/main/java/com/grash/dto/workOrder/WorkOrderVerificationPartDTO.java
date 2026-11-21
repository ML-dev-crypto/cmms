package com.grash.dto.workOrder;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Part consumed by a work order, shown to a verifier")
public class WorkOrderVerificationPartDTO {

    private Long id;
    private String name;
    private double quantity;
    private double cost;
}
