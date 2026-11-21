package com.grash.mapper;

import com.grash.dto.DepartmentMiniDTO;
import com.grash.model.Department;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DepartmentMapper {

    @Mapping(source = "hospital.id", target = "hospitalId")
    DepartmentMiniDTO toMiniDto(Department department);
}
