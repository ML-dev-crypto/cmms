package com.grash.service;

import com.grash.advancedsearch.FilterField;
import com.grash.advancedsearch.SearchCriteria;
import com.grash.model.Asset;
import com.grash.model.Company;
import com.grash.model.Department;
import com.grash.model.Hospital;
import com.grash.model.Location;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.UserScope;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.repository.UserScopeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataScopeServiceTest {

    @InjectMocks
    private DataScopeService dataScopeService;

    @Mock
    private UserScopeRepository userScopeRepository;

    private User user;
    private Hospital hospital;
    private Department department;
    private Location location;

    @BeforeEach
    void setUp() {
        Company company = new Company();
        company.setId(1L);
        Role role = Role.builder()
                .id(2L)
                .roleType(RoleType.ROLE_CLIENT)
                .code(RoleCode.USER_CREATED)
                .build();
        user = new User();
        user.setId(3L);
        user.setCompany(company);
        user.setRole(role);

        hospital = new Hospital();
        hospital.setId(10L);
        hospital.setName("Central Hospital");

        department = new Department();
        department.setId(20L);
        department.setName("Radiology");
        department.setHospital(hospital);

        location = new Location();
        location.setId(30L);
        location.setName("MRI room");
        location.setHospital(hospital);
        location.setDepartment(department);
    }

    private UserScope scope(Hospital hospital, Department department, Location location) {
        UserScope scope = new UserScope();
        scope.setUser(user);
        scope.setCompany(user.getCompany());
        scope.setHospital(hospital);
        scope.setDepartment(department);
        scope.setLocation(location);
        return scope;
    }

    @Test
    void userWithoutScopeKeepsFullCompanyAccess() {
        when(userScopeRepository.existsByUser_Id(3L)).thenReturn(false);

        assertFalse(dataScopeService.isRestricted(user));
        assertTrue(dataScopeService.canAccessHospital(user, hospital));
        assertTrue(dataScopeService.canAccessAsset(user, new Asset()));

        SearchCriteria criteria = SearchCriteria.builder()
                .filterFields(new ArrayList<>())
                .build();
        dataScopeService.applyAssetScope(user, criteria);
        assertTrue(criteria.getFilterFields().isEmpty());
    }

    @Test
    void hospitalScopeGrantsAccessToItsHierarchyOnly() {
        when(userScopeRepository.existsByUser_Id(3L)).thenReturn(true);
        when(userScopeRepository.findByUser_IdAndCompany_Id(3L, 1L))
                .thenReturn(List.of(scope(hospital, null, null)));

        assertTrue(dataScopeService.isRestricted(user));
        assertTrue(dataScopeService.canAccessHospital(user, hospital));
        assertTrue(dataScopeService.canAccessDepartment(user, department));
        assertTrue(dataScopeService.canAccessLocation(user, location));

        Asset assetInDepartment = new Asset();
        assetInDepartment.setDepartment(department);
        assertTrue(dataScopeService.canAccessAsset(user, assetInDepartment));

        Asset assetInLocation = new Asset();
        assetInLocation.setLocation(location);
        assertTrue(dataScopeService.canAccessAsset(user, assetInLocation));

        Hospital otherHospital = new Hospital();
        otherHospital.setId(99L);
        assertFalse(dataScopeService.canAccessHospital(user, otherHospital));
        assertFalse(dataScopeService.canAccessAsset(user, new Asset()));
    }

    @Test
    void departmentScopeImplicitlyGrantsItsHospital() {
        when(userScopeRepository.existsByUser_Id(3L)).thenReturn(true);
        when(userScopeRepository.findByUser_IdAndCompany_Id(3L, 1L))
                .thenReturn(List.of(scope(null, department, null)));

        DataScopeService.ScopeInfo scope = dataScopeService.getScope(user);
        assertTrue(scope.departmentIds().contains(20L));
        assertTrue(scope.hospitalIds().contains(10L));
        assertTrue(dataScopeService.canAccessDepartment(user, department));
    }

    @Test
    void restrictedUserWithoutResolvableScopeGetsEmptyAssetSearch() {
        when(userScopeRepository.existsByUser_Id(3L)).thenReturn(true);
        when(userScopeRepository.findByUser_IdAndCompany_Id(3L, 1L))
                .thenReturn(List.of(new UserScope()));

        SearchCriteria criteria = SearchCriteria.builder()
                .filterFields(new ArrayList<>())
                .build();
        dataScopeService.applyAssetScope(user, criteria);

        assertEquals(1, criteria.getFilterFields().size());
        FilterField filter = criteria.getFilterFields().get(0);
        assertEquals("id", filter.getField());
        assertEquals("eq", filter.getOperation());
        assertEquals(-1L, filter.getValue());
    }

    @Test
    void applyAssetScopeAddsOrAlternativesForScopedRelations() {
        when(userScopeRepository.existsByUser_Id(3L)).thenReturn(true);
        when(userScopeRepository.findByUser_IdAndCompany_Id(3L, 1L))
                .thenReturn(List.of(scope(hospital, department, null)));

        SearchCriteria criteria = SearchCriteria.builder()
                .filterFields(new ArrayList<>())
                .build();
        dataScopeService.applyAssetScope(user, criteria);

        assertEquals(1, criteria.getFilterFields().size());
        FilterField main = criteria.getFilterFields().get(0);
        assertEquals("department", main.getField());
        assertEquals("in", main.getOperation());
        assertNotNull(main.getAlternatives());
        assertTrue(main.getAlternatives().stream()
                .anyMatch(alternative -> "department.hospital".equals(alternative.getField())));
        assertTrue(main.getAlternatives().stream()
                .anyMatch(alternative -> "location.hospital".equals(alternative.getField())));
        assertTrue(main.getAlternatives().stream()
                .anyMatch(alternative -> "location.department".equals(alternative.getField())));
    }

    @Test
    void superAdminIsNeverRestricted() {
        user.getRole().setRoleType(RoleType.ROLE_SUPER_ADMIN);

        assertFalse(dataScopeService.isRestricted(user));
        verify(userScopeRepository, never()).existsByUser_Id(anyLong());
    }
}
