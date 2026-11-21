package com.grash.service;

import com.grash.exception.CustomException;
import com.grash.model.Department;
import com.grash.model.Hospital;
import com.grash.model.Location;
import com.grash.model.User;
import com.grash.model.UserScope;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import com.grash.repository.DepartmentRepository;
import com.grash.repository.HospitalRepository;
import com.grash.repository.LocationRepository;
import com.grash.repository.UserScopeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserScopeService {

    private final UserScopeRepository userScopeRepository;
    private final UserService userService;
    private final HospitalRepository hospitalRepository;
    private final DepartmentRepository departmentRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public List<UserScope> findByUser(Long userId, User requester) {
        User target = userService.findByIdAndCompany(userId, requester.getCompany().getId())
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        if (!canManage(requester) && !requester.getId().equals(target.getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        return userScopeRepository.findByUser_IdAndCompany_Id(userId, requester.getCompany().getId());
    }

    @Transactional(readOnly = true)
    public List<UserScope> findMine(User requester) {
        return userScopeRepository.findByUser_IdAndCompany_Id(requester.getId(),
                requester.getCompany().getId());
    }

    @Transactional
    public List<UserScope> replaceForUser(Long userId, List<UserScope> scopes, User requester) {
        if (!canManage(requester)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
        User target = userService.findByIdAndCompany(userId, requester.getCompany().getId())
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        List<UserScope> resolved = new ArrayList<>();
        if (scopes != null) {
            for (UserScope scope : scopes) {
                resolved.add(resolveScope(scope, target, requester));
            }
        }
        userScopeRepository.deleteByUser_IdAndCompany_Id(userId, requester.getCompany().getId());
        return userScopeRepository.saveAll(resolved);
    }

    private UserScope resolveScope(UserScope input, User target, User requester) {
        UserScope scope = new UserScope();
        scope.setUser(target);
        scope.setCompany(requester.getCompany());
        scope.setPrimaryScope(input.isPrimaryScope());
        Long companyId = requester.getCompany().getId();

        if (input.getHospital() != null && input.getHospital().getId() != null) {
            Hospital hospital = hospitalRepository.findByIdAndCompany_Id(input.getHospital().getId(), companyId)
                    .orElseThrow(() -> new CustomException("Hospital not found", HttpStatus.NOT_FOUND));
            scope.setHospital(hospital);
        }
        if (input.getDepartment() != null && input.getDepartment().getId() != null) {
            Department department = departmentRepository.findByIdAndCompany_Id(input.getDepartment().getId(), companyId)
                    .orElseThrow(() -> new CustomException("Department not found", HttpStatus.NOT_FOUND));
            if (scope.getHospital() != null && department.getHospital() != null
                    && !department.getHospital().getId().equals(scope.getHospital().getId())) {
                throw new CustomException("Department does not belong to the selected hospital",
                        HttpStatus.NOT_ACCEPTABLE);
            }
            scope.setDepartment(department);
            if (scope.getHospital() == null) {
                scope.setHospital(department.getHospital());
            }
        }
        if (input.getLocation() != null && input.getLocation().getId() != null) {
            Location location = locationRepository.findByIdAndCompany_Id(input.getLocation().getId(), companyId)
                    .orElseThrow(() -> new CustomException("Location not found", HttpStatus.NOT_FOUND));
            scope.setLocation(location);
        }
        if (scope.getHospital() == null && scope.getDepartment() == null && scope.getLocation() == null) {
            throw new CustomException("A scope needs a hospital, a department or a location",
                    HttpStatus.NOT_ACCEPTABLE);
        }
        return scope;
    }

    private boolean canManage(User user) {
        if (user.getRole() == null) {
            return false;
        }
        RoleCode code = user.getRole().getCode();
        return RoleCode.ADMIN.equals(code) || RoleCode.LIMITED_ADMIN.equals(code)
                || user.getRole().getEditOtherPermissions().contains(PermissionEntity.PEOPLE_AND_TEAMS)
                || user.getRole().getCreatePermissions().contains(PermissionEntity.PEOPLE_AND_TEAMS);
    }
}
