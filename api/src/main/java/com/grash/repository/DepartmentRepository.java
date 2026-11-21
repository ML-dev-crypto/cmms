package com.grash.repository;

import com.grash.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Collection<Department> findByCompany_IdOrderByNameAsc(Long companyId);

    Collection<Department> findByHospital_IdAndCompany_IdOrderByNameAsc(Long hospitalId, Long companyId);

    Optional<Department> findByIdAndCompany_Id(Long id, Long companyId);

    Optional<Department> findByCodeIgnoreCaseAndHospital_Id(String code, Long hospitalId);

    long countByHospital_Id(Long hospitalId);

    long countByParentDepartment_Id(Long parentDepartmentId);
}
