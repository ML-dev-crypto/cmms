package com.grash.controller;

import com.grash.dto.DepartmentMiniDTO;
import com.grash.dto.DepartmentPatchDTO;
import com.grash.dto.SuccessResponse;
import com.grash.mapper.DepartmentMapper;
import com.grash.model.Department;
import com.grash.model.User;
import com.grash.security.CurrentUser;
import com.grash.service.DepartmentService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/departments")
@Tag(name = "Departments", description = "Department master data")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;
    private final DepartmentMapper departmentMapper;

    @GetMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<Department> getAll(@RequestParam(required = false) Long hospitalId,
                                         @Parameter(hidden = true) @CurrentUser User user) {
        return departmentService.findAll(hospitalId, user);
    }

    @GetMapping("/mini")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Collection<DepartmentMiniDTO> getMini(@RequestParam(required = false) Long hospitalId,
                                                 @Parameter(hidden = true) @CurrentUser User user) {
        return departmentService.findAll(hospitalId, user).stream().map(departmentMapper::toMiniDto)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Department getById(@PathVariable Long id, @Parameter(hidden = true) @CurrentUser User user) {
        return departmentService.getById(id, user);
    }

    @PostMapping("")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Department create(@Valid @RequestBody Department department,
                             @Parameter(hidden = true) @CurrentUser User user) {
        return departmentService.create(department, user);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public Department patch(@PathVariable Long id, @Valid @RequestBody DepartmentPatchDTO department,
                            @Parameter(hidden = true) @CurrentUser User user) {
        return departmentService.patch(id, department, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CLIENT')")
    public ResponseEntity<SuccessResponse> delete(@PathVariable Long id,
                                                  @Parameter(hidden = true) @CurrentUser User user) {
        departmentService.delete(id, user);
        return ResponseEntity.ok(new SuccessResponse(true, "Deleted successfully"));
    }
}
