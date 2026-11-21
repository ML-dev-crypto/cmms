package com.grash.mapper;

import com.grash.dto.WorkOrderBaseMiniDTO;
import com.grash.dto.workOrder.WorkOrderMiniDTO;
import com.grash.dto.workOrder.WorkOrderPatchDTO;
import com.grash.dto.workOrder.WorkOrderShowDTO;
import com.grash.dto.workOrder.WorkOrderPostDTO;
import com.grash.dto.workload.WorkloadWorkOrderDTO;
import com.grash.model.WorkOrder;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {PartMapper.class, FileMapper.class, LocationMapper.class
        , TeamMapper.class, UserMapper.class,
        CustomerMapper.class, AssetMapper.class, CustomFieldValueMapper.class})
public interface WorkOrderMapper {
    WorkOrder updateWorkOrder(@MappingTarget WorkOrder entity, WorkOrderPatchDTO dto);

    @Mappings({})
    WorkOrderPatchDTO toPatchDto(WorkOrder model);

    @Mappings({
            @Mapping(source = "parentRequest.audioDescription", target = "audioDescription"),
            @Mapping(target = "image", source = "image", qualifiedByName = "toThumbnailDto"),

    })
    WorkOrderShowDTO toShowDto(WorkOrder model);

    WorkOrderMiniDTO toMiniDto(WorkOrder model);

    WorkOrderBaseMiniDTO toBaseMiniDto(WorkOrder model);

    @Mapping(target = "lifecycleState", ignore = true)
    @Mapping(target = "status", ignore = true)
    WorkOrder fromPostDto(WorkOrderPostDTO workOrderPostDTO);

    WorkloadWorkOrderDTO toWorkloadDto(WorkOrder model);

}
