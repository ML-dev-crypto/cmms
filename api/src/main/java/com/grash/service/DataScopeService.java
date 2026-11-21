package com.grash.service;

import com.grash.advancedsearch.FilterField;
import com.grash.advancedsearch.SearchCriteria;
import com.grash.model.Asset;
import com.grash.model.Department;
import com.grash.model.Hospital;
import com.grash.model.Location;
import com.grash.model.User;
import com.grash.model.UserScope;
import com.grash.model.enums.RoleType;
import com.grash.repository.UserScopeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side foundation for data scoping.
 *
 * <p>A user without any {@link UserScope} rows keeps full access inside the
 * company. As soon as one row exists, the union of the scoped hospitals,
 * departments and locations becomes the user's accessible perimeter. The rule
 * is intentionally additive and backwards compatible with existing tenants.
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final UserScopeRepository userScopeRepository;

    public record ScopeInfo(Set<Long> hospitalIds, Set<Long> departmentIds, Set<Long> locationIds) {
        public boolean isEmpty() {
            return hospitalIds.isEmpty() && departmentIds.isEmpty() && locationIds.isEmpty();
        }
    }

    @Transactional(readOnly = true)
    public ScopeInfo getScope(User user) {
        if (isUnrestricted(user)) {
            return new ScopeInfo(Set.of(), Set.of(), Set.of());
        }
        List<UserScope> scopes = userScopeRepository.findByUser_IdAndCompany_Id(user.getId(),
                user.getCompany().getId());
        Set<Long> hospitalIds = new HashSet<>();
        Set<Long> departmentIds = new HashSet<>();
        Set<Long> locationIds = new HashSet<>();
        for (UserScope scope : scopes) {
            if (scope.getHospital() != null) {
                hospitalIds.add(scope.getHospital().getId());
            }
            if (scope.getDepartment() != null) {
                departmentIds.add(scope.getDepartment().getId());
                if (scope.getDepartment().getHospital() != null) {
                    hospitalIds.add(scope.getDepartment().getHospital().getId());
                }
            }
            if (scope.getLocation() != null) {
                locationIds.add(scope.getLocation().getId());
                if (scope.getLocation().getDepartment() != null) {
                    departmentIds.add(scope.getLocation().getDepartment().getId());
                }
                if (scope.getLocation().getHospital() != null) {
                    hospitalIds.add(scope.getLocation().getHospital().getId());
                }
            }
        }
        return new ScopeInfo(hospitalIds, departmentIds, locationIds);
    }

    public boolean isRestricted(User user) {
        if (isUnrestricted(user)) {
            return false;
        }
        return userScopeRepository.existsByUser_Id(user.getId());
    }

    public boolean isUnrestricted(User user) {
        return user.getRole() != null && RoleType.ROLE_SUPER_ADMIN.equals(user.getRole().getRoleType());
    }

    @Transactional(readOnly = true)
    public boolean canAccessHospital(User user, Hospital hospital) {
        if (!isRestricted(user)) {
            return true;
        }
        return getScope(user).hospitalIds().contains(hospital.getId());
    }

    @Transactional(readOnly = true)
    public boolean canAccessDepartment(User user, Department department) {
        if (!isRestricted(user)) {
            return true;
        }
        ScopeInfo scope = getScope(user);
        return scope.departmentIds().contains(department.getId())
                || (department.getHospital() != null && scope.hospitalIds().contains(department.getHospital().getId()));
    }

    @Transactional(readOnly = true)
    public boolean canAccessLocation(User user, Location location) {
        if (!isRestricted(user)) {
            return true;
        }
        ScopeInfo scope = getScope(user);
        if (scope.locationIds().contains(location.getId())) {
            return true;
        }
        if (location.getDepartment() != null && scope.departmentIds().contains(location.getDepartment().getId())) {
            return true;
        }
        return location.getHospital() != null && scope.hospitalIds().contains(location.getHospital().getId());
    }

    @Transactional(readOnly = true)
    public boolean canAccessAsset(User user, Asset asset) {
        if (!isRestricted(user)) {
            return true;
        }
        ScopeInfo scope = getScope(user);
        if (asset.getDepartment() != null) {
            Department department = asset.getDepartment();
            if (scope.departmentIds().contains(department.getId())) {
                return true;
            }
            if (department.getHospital() != null && scope.hospitalIds().contains(department.getHospital().getId())) {
                return true;
            }
        }
        if (asset.getLocation() != null) {
            Location location = asset.getLocation();
            if (scope.locationIds().contains(location.getId())) {
                return true;
            }
            if (location.getDepartment() != null && scope.departmentIds().contains(location.getDepartment().getId())) {
                return true;
            }
            return location.getHospital() != null && scope.hospitalIds().contains(location.getHospital().getId());
        }
        return false;
    }

    public List<Hospital> filterHospitals(User user, List<Hospital> hospitals) {
        if (!isRestricted(user)) {
            return hospitals;
        }
        ScopeInfo scope = getScope(user);
        return hospitals.stream().filter(h -> scope.hospitalIds().contains(h.getId())).toList();
    }

    /**
     * Applies the scope to an asset search by OR-ing the accessible
     * departments, hospitals and locations. When the user has scopes but none
     * of them covers an asset relation, a deliberately empty filter is added.
     */
    public void applyAssetScope(User user, SearchCriteria searchCriteria) {
        if (!isRestricted(user)) {
            return;
        }
        ScopeInfo scope = getScope(user);
        List<FilterField> alternatives = new ArrayList<>();
        if (!scope.departmentIds().isEmpty()) {
            alternatives.add(inFilter("department", scope.departmentIds()));
        }
        if (!scope.hospitalIds().isEmpty()) {
            alternatives.add(inFilter("department.hospital", scope.hospitalIds()));
            alternatives.add(inFilter("location.hospital", scope.hospitalIds()));
        }
        if (!scope.departmentIds().isEmpty()) {
            alternatives.add(inFilter("location.department", scope.departmentIds()));
        }
        if (!scope.locationIds().isEmpty()) {
            alternatives.add(inFilter("location", scope.locationIds()));
        }
        if (alternatives.isEmpty()) {
            searchCriteria.getFilterFields().add(FilterField.builder()
                    .field("id")
                    .operation("eq")
                    .value(-1L)
                    .values(new ArrayList<>())
                    .build());
            return;
        }
        FilterField main = alternatives.remove(0);
        main.setAlternatives(alternatives);
        searchCriteria.getFilterFields().add(main);
    }

    private FilterField inFilter(String field, Set<Long> ids) {
        return FilterField.builder()
                .field(field)
                .operation("in")
                .values(new ArrayList<>(ids))
                .build();
    }
}
