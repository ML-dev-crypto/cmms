package com.grash.repository;

import com.grash.model.Hospital;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface HospitalRepository extends JpaRepository<Hospital, Long> {

    Collection<Hospital> findByCompany_IdOrderByNameAsc(Long companyId);

    Optional<Hospital> findByIdAndCompany_Id(Long id, Long companyId);

    Optional<Hospital> findByCodeIgnoreCaseAndCompany_Id(String code, Long companyId);

    long countByCompany_Id(Long companyId);
}
