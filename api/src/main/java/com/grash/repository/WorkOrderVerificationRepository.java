package com.grash.repository;

import com.grash.model.WorkOrderVerification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Read/append repository for verification records. Intentionally exposes no update/delete methods.
 */
public interface WorkOrderVerificationRepository extends JpaRepository<WorkOrderVerification, Long> {

    @EntityGraph(attributePaths = {"verifier"})
    List<WorkOrderVerification> findByWorkOrder_IdOrderByCreatedAtAscIdAsc(Long workOrderId);

    @EntityGraph(attributePaths = {"verifier"})
    Optional<WorkOrderVerification> findFirstByWorkOrder_IdOrderByCreatedAtDescIdDesc(Long workOrderId);

    long countByWorkOrder_Id(Long workOrderId);
}
