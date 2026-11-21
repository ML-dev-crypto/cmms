package com.grash.repository;

import com.grash.model.UserScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface UserScopeRepository extends JpaRepository<UserScope, Long> {

    List<UserScope> findByUser_IdAndCompany_Id(Long userId, Long companyId);

    Collection<UserScope> findByCompany_Id(Long companyId);

    void deleteByUser_IdAndCompany_Id(Long userId, Long companyId);

    boolean existsByUser_Id(Long userId);
}
