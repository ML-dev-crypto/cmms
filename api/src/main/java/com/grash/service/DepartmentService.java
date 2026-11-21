package com.grash.service;

import com.grash.dto.DepartmentPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Department;
import com.grash.model.Hospital;
import com.grash.model.User;
import com.grash.repository.AssetRepository;
import com.grash.repository.DepartmentRepository;
import com.grash.repository.HospitalRepository;
import com.grash.repository.LocationRepository;
import com.grash.utils.Sanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final HospitalRepository hospitalRepository;
    private final LocationRepository locationRepository;
    private final AssetRepository assetRepository;
    private final DataScopeService dataScopeService;

    @Transactional
    public Department create(Department department, User user) {
        if (!department.canBeEditedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        sanitize(department);
        validate(department, user, null);
        department.setCompany(user.getCompany());
        return departmentRepository.saveAndFlush(department);
    }

    @Transactional
    public Department patch(Long id, DepartmentPatchDTO patch, User user) {
        Department saved = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        if (!saved.canBeEditedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (patch.getName() != null) saved.setName(patch.getName());
        if (patch.getCode() != null) saved.setCode(patch.getCode());
        if (patch.getDescription() != null) saved.setDescription(patch.getDescription());
        if (patch.getHospital() != null) saved.setHospital(patch.getHospital());
        if (patch.getParentDepartment() != null) saved.setParentDepartment(patch.getParentDepartment());
        if (patch.getActive() != null) saved.setActive(patch.getActive());
        sanitize(saved);
        validate(saved, user, id);
        return departmentRepository.saveAndFlush(saved);
    }

    @Transactional(readOnly = true)
    public Collection<Department> findAll(Long hospitalId, User user) {
        if (!canView(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<Department> departments = (hospitalId == null
                ? departmentRepository.findByCompany_IdOrderByNameAsc(user.getCompany().getId())
                : departmentRepository.findByHospital_IdAndCompany_IdOrderByNameAsc(hospitalId,
                user.getCompany().getId())).stream().toList();
        if (!dataScopeService.isRestricted(user)) {
            return departments;
        }
        return departments.stream().filter(department -> dataScopeService.canAccessDepartment(user, department))
                .toList();
    }

    public Department getById(Long id, User user) {
        Department department = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        if (!department.canBeViewedBy(user) || !dataScopeService.canAccessDepartment(user, department)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return department;
    }

    @Transactional
    public void delete(Long id, User user) {
        Department department = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
        if (!department.canBeDeletedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (departmentRepository.countByParentDepartment_Id(id) > 0) {
            throw new CustomException("Department still has child departments", HttpStatus.NOT_ACCEPTABLE);
        }
        if (locationRepository.countByDepartment_Id(id) > 0) {
            throw new CustomException("Department is still used by locations", HttpStatus.NOT_ACCEPTABLE);
        }
        if (assetRepository.countByDepartment_Id(id) > 0) {
            throw new CustomException("Department is still used by assets", HttpStatus.NOT_ACCEPTABLE);
        }
        departmentRepository.delete(department);
    }

    public Optional<Department> findByIdAndCompany(Long id, Long companyId) {
        return departmentRepository.findByIdAndCompany_Id(id, companyId);
    }

    private boolean canView(User user) {
        return user.getRole().getRoleType().equals(com.grash.model.enums.RoleType.ROLE_SUPER_ADMIN)
                || new Department().canBeViewedBy(user);
    }

    private void validate(Department department, User user, Long currentId) {
        if (department.getName() == null || department.getName().isBlank()) {
            throw new CustomException("Department name is required", HttpStatus.NOT_ACCEPTABLE);
        }
        if (department.getHospital() == null || department.getHospital().getId() == null) {
            throw new CustomException("Hospital is required", HttpStatus.NOT_ACCEPTABLE);
        }
        Hospital hospital = hospitalRepository.findByIdAndCompany_Id(department.getHospital().getId(),
                        user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Hospital not found", HttpStatus.NOT_FOUND));
        department.setHospital(hospital);
        if (department.getParentDepartment() != null) {
            if (department.getParentDepartment().getId() == null) {
                department.setParentDepartment(null);
            } else {
                Department parent = findByIdAndCompany(department.getParentDepartment().getId(),
                                user.getCompany().getId())
                        .orElseThrow(() -> new CustomException("Parent department not found", HttpStatus.NOT_FOUND));
                if (currentId != null && currentId.equals(parent.getId())) {
                    throw new CustomException("Parent department cannot be the same department",
                            HttpStatus.NOT_ACCEPTABLE);
                }
                if (parent.getHospital() != null && !parent.getHospital().getId().equals(hospital.getId())) {
                    throw new CustomException("Parent department must belong to the same hospital",
                            HttpStatus.NOT_ACCEPTABLE);
                }
                department.setParentDepartment(parent);
            }
        }
        if (department.getCode() == null || department.getCode().isBlank()) {
            department.setCode(null);
            return;
        }
        department.setCode(department.getCode().trim().toUpperCase(Locale.ROOT));
        departmentRepository.findByCodeIgnoreCaseAndHospital_Id(department.getCode(), hospital.getId())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new CustomException("Department code already exists in this hospital",
                            HttpStatus.NOT_ACCEPTABLE);
                });
    }

    private void sanitize(Department department) {
        department.setName(Sanitizer.cleanText(department.getName()));
        department.setDescription(Sanitizer.cleanText(department.getDescription()));
        if (department.getCode() != null) {
            department.setCode(Sanitizer.cleanText(department.getCode()));
        }
    }
}
