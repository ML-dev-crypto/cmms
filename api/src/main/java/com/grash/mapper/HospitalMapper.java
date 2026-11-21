package com.grash.mapper;

import com.grash.dto.HospitalMiniDTO;
import com.grash.model.Hospital;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface HospitalMapper {
    HospitalMiniDTO toMiniDto(Hospital hospital);
}
