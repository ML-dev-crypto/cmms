package com.grash.service;

import com.grash.dto.HospitalPatchDTO;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Hospital;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.RoleCode;
import com.grash.model.enums.RoleType;
import com.grash.repository.DepartmentRepository;
import com.grash.repository.HospitalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalServiceTest {

    @InjectMocks
    private HospitalService hospitalService;

    @Mock
    private HospitalRepository hospitalRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DataScopeService dataScopeService;

    private Company company;
    private User admin;
    private User technician;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(1L);

        Role adminRole = Role.builder()
                .id(1L)
                .roleType(RoleType.ROLE_CLIENT)
                .code(RoleCode.ADMIN)
                .viewPermissions(new HashSet<>())
                .createPermissions(new HashSet<>())
                .editOtherPermissions(new HashSet<>())
                .deleteOtherPermissions(new HashSet<>())
                .build();
        admin = new User();
        admin.setId(1L);
        admin.setCompany(company);
        admin.setRole(adminRole);

        Role technicianRole = Role.builder()
                .id(2L)
                .roleType(RoleType.ROLE_CLIENT)
                .code(RoleCode.TECHNICIAN)
                .viewPermissions(new HashSet<>())
                .createPermissions(new HashSet<>())
                .editOtherPermissions(new HashSet<>())
                .deleteOtherPermissions(new HashSet<>())
                .build();
        technician = new User();
        technician.setId(2L);
        technician.setCompany(company);
        technician.setRole(technicianRole);
    }

    private Hospital hospital(String name) {
        Hospital hospital = new Hospital();
        hospital.setName(name);
        return hospital;
    }

    @Test
    void createNormalizesCodeAndAssignsCompany() {
        Hospital hospital = hospital("  Central Hospital  ");
        hospital.setCode(" h1 ");
        when(hospitalRepository.saveAndFlush(any(Hospital.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(hospitalRepository.findByCodeIgnoreCaseAndCompany_Id("H1", 1L))
                .thenReturn(Optional.empty());

        Hospital created = hospitalService.create(hospital, admin);

        assertEquals("H1", created.getCode());
        assertSame(company, created.getCompany());
        verify(hospitalRepository).saveAndFlush(hospital);
    }

    @Test
    void createRejectsUserWithoutPermission() {
        CustomException exception = assertThrows(CustomException.class,
                () -> hospitalService.create(hospital("Lab"), technician));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(hospitalRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        Hospital hospital = hospital("Central");
        hospital.setCode("H1");
        Hospital existing = new Hospital();
        existing.setId(5L);
        when(hospitalRepository.findByCodeIgnoreCaseAndCompany_Id("H1", 1L))
                .thenReturn(Optional.of(existing));

        CustomException exception = assertThrows(CustomException.class,
                () -> hospitalService.create(hospital, admin));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, exception.getHttpStatus());
    }

    @Test
    void createRejectsBlankName() {
        Hospital hospital = hospital("   ");

        CustomException exception = assertThrows(CustomException.class,
                () -> hospitalService.create(hospital, admin));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, exception.getHttpStatus());
    }

    @Test
    void deleteIsBlockedWhileDepartmentsRemain() {
        Hospital hospital = hospital("Central");
        hospital.setId(10L);
        when(hospitalRepository.findByIdAndCompany_Id(10L, 1L))
                .thenReturn(Optional.of(hospital));
        when(departmentRepository.countByHospital_Id(10L)).thenReturn(2L);

        CustomException exception = assertThrows(CustomException.class,
                () -> hospitalService.delete(10L, admin));
        assertEquals(HttpStatus.NOT_ACCEPTABLE, exception.getHttpStatus());
        verify(hospitalRepository, never()).delete(any());
    }

    @Test
    void findAllAppliesDataScope() {
        Hospital h1 = hospital("Central");
        h1.setId(10L);
        Hospital h2 = hospital("North");
        h2.setId(11L);
        when(hospitalRepository.findByCompany_IdOrderByNameAsc(1L))
                .thenReturn(List.of(h1, h2));
        when(dataScopeService.filterHospitals(admin, List.of(h1, h2)))
                .thenReturn(List.of(h1));

        var result = hospitalService.findAll(admin);

        assertEquals(1, result.size());
        assertTrue(result.contains(h1));
        verify(dataScopeService).filterHospitals(admin, List.of(h1, h2));
    }

    @Test
    void patchKeepsUntouchedFields() {
        Hospital hospital = hospital("Central");
        hospital.setId(10L);
        hospital.setCode("H1");
        hospital.setPhone("555-0100");
        when(hospitalRepository.findByIdAndCompany_Id(10L, 1L))
                .thenReturn(Optional.of(hospital));
        when(hospitalRepository.findByCodeIgnoreCaseAndCompany_Id("H1", 1L))
                .thenReturn(Optional.of(hospital));
        when(hospitalRepository.saveAndFlush(any(Hospital.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HospitalPatchDTO patch = new HospitalPatchDTO();
        patch.setName("Central Updated");

        Hospital updated = hospitalService.patch(10L, patch, admin);

        assertEquals("Central Updated", updated.getName());
        assertEquals("H1", updated.getCode());
        assertEquals("555-0100", updated.getPhone());
    }

    @Test
    void roleWithNewPermissionCanViewHospitals() {
        technician.getRole().getViewPermissions().add(PermissionEntity.HOSPITALS_AND_DEPARTMENTS);
        when(hospitalRepository.findByCompany_IdOrderByNameAsc(1L))
                .thenReturn(List.of());
        when(dataScopeService.filterHospitals(technician, List.of()))
                .thenReturn(List.of());

        assertDoesNotThrow(() -> hospitalService.findAll(technician));
    }

    @Test
    void getByIdChecksDataScope() {
        Hospital hospital = hospital("Central");
        hospital.setId(10L);
        when(hospitalRepository.findByIdAndCompany_Id(10L, 1L))
                .thenReturn(Optional.of(hospital));
        when(dataScopeService.canAccessHospital(admin, hospital)).thenReturn(true);

        assertDoesNotThrow(() -> hospitalService.getById(10L, admin));
        verify(dataScopeService).canAccessHospital(admin, hospital);
    }
}
