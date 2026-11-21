package com.grash.service;

import com.grash.dto.HospitalPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Department;
import com.grash.model.Hospital;
import com.grash.model.User;
import com.grash.repository.DepartmentRepository;
import com.grash.repository.HospitalRepository;
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
public class HospitalService {

    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final DataScopeService dataScopeService;

    @Transactional
    public Hospital create(Hospital hospital, User user) {
        if (!hospital.canBeEditedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        sanitize(hospital);
        validate(hospital, user, null);
        hospital.setCompany(user.getCompany());
        return hospitalRepository.saveAndFlush(hospital);
    }

    @Transactional
    public Hospital patch(Long id, HospitalPatchDTO patch, User user) {
        Hospital saved = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Hospital not found", HttpStatus.NOT_FOUND));
        if (!saved.canBeEditedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (patch.getName() != null) saved.setName(patch.getName());
        if (patch.getCode() != null) saved.setCode(patch.getCode());
        if (patch.getAddress() != null) saved.setAddress(patch.getAddress());
        if (patch.getPhone() != null) saved.setPhone(patch.getPhone());
        if (patch.getEmail() != null) saved.setEmail(patch.getEmail());
        if (patch.getTimezone() != null) saved.setTimezone(patch.getTimezone());
        if (patch.getDescription() != null) saved.setDescription(patch.getDescription());
        if (patch.getActive() != null) saved.setActive(patch.getActive());
        sanitize(saved);
        validate(saved, user, id);
        return hospitalRepository.saveAndFlush(saved);
    }

    public Collection<Hospital> findAll(User user) {
        if (!canView(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        List<Hospital> hospitals = hospitalRepository.findByCompany_IdOrderByNameAsc(user.getCompany().getId())
                .stream().toList();
        return dataScopeService.filterHospitals(user, hospitals);
    }

    public Collection<Hospital> findMini(User user) {
        return findAll(user);
    }

    public Hospital getById(Long id, User user) {
        Hospital hospital = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Hospital not found", HttpStatus.NOT_FOUND));
        if (!hospital.canBeViewedBy(user) || !dataScopeService.canAccessHospital(user, hospital)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return hospital;
    }

    @Transactional
    public void delete(Long id, User user) {
        Hospital hospital = findByIdAndCompany(id, user.getCompany().getId())
                .orElseThrow(() -> new CustomException("Hospital not found", HttpStatus.NOT_FOUND));
        if (!hospital.canBeDeletedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        if (departmentRepository.countByHospital_Id(id) > 0) {
            throw new CustomException("Hospital still has departments", HttpStatus.NOT_ACCEPTABLE);
        }
        hospitalRepository.delete(hospital);
    }

    public Optional<Hospital> findByIdAndCompany(Long id, Long companyId) {
        return hospitalRepository.findByIdAndCompany_Id(id, companyId);
    }

    private boolean canView(User user) {
        return user.getRole().getRoleType().equals(com.grash.model.enums.RoleType.ROLE_SUPER_ADMIN)
                || new Hospital().canBeViewedBy(user);
    }

    private void validate(Hospital hospital, User user, Long currentId) {
        if (hospital.getName() == null || hospital.getName().isBlank()) {
            throw new CustomException("Hospital name is required", HttpStatus.NOT_ACCEPTABLE);
        }
        if (hospital.getCode() == null || hospital.getCode().isBlank()) {
            hospital.setCode(null);
            return;
        }
        hospital.setCode(hospital.getCode().trim().toUpperCase(Locale.ROOT));
        hospitalRepository.findByCodeIgnoreCaseAndCompany_Id(hospital.getCode(), user.getCompany().getId())
                .filter(existing -> currentId == null || !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new CustomException("Hospital code already exists", HttpStatus.NOT_ACCEPTABLE);
                });
    }

    private void sanitize(Hospital hospital) {
        hospital.setName(Sanitizer.cleanText(hospital.getName()));
        hospital.setAddress(Sanitizer.cleanText(hospital.getAddress()));
        hospital.setDescription(Sanitizer.cleanText(hospital.getDescription()));
        hospital.setEmail(Sanitizer.cleanText(hospital.getEmail()));
        hospital.setPhone(Sanitizer.cleanText(hospital.getPhone()));
        hospital.setTimezone(Sanitizer.cleanText(hospital.getTimezone()));
        if (hospital.getCode() != null) {
            hospital.setCode(Sanitizer.cleanText(hospital.getCode()));
        }
    }
}
