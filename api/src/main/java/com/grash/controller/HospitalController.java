package com.grash.controller;

import com.grash.dto.HospitalMiniDTO;
import com.grash.dto.HospitalPatchDTO;
import com.grash.dto.SuccessResponse;
import com.grash.mapper.HospitalMapper;
import com.grash.model.Hospital;
import com.grash.model.User;
import com.grash.security.CurrentUser;
import com.grash.service.HospitalService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/hospitals")
@Tag(name = "Hospitals", description = "Hospital master data")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalService hospitalService;
    private final HospitalMapper hospitalMapper;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<Hospital> getAll(@Parameter(hidden = true) @CurrentUser User user) {
        return hospitalService.findAll(user);
    }

    @GetMapping("/mini")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<HospitalMiniDTO> getMini(@Parameter(hidden = true) @CurrentUser User user) {
        return hospitalService.findMini(user).stream().map(hospitalMapper::toMiniDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Hospital getById(@PathVariable Long id, @Parameter(hidden = true) @CurrentUser User user) {
        return hospitalService.getById(id, user);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Hospital create(@Valid @RequestBody Hospital hospital,
                           @Parameter(hidden = true) @CurrentUser User user) {
        return hospitalService.create(hospital, user);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Hospital patch(@PathVariable Long id, @Valid @RequestBody HospitalPatchDTO hospital,
                          @Parameter(hidden = true) @CurrentUser User user) {
        return hospitalService.patch(id, hospital, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable Long id,
                                                  @Parameter(hidden = true) @CurrentUser User user) {
        hospitalService.delete(id, user);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }
}
