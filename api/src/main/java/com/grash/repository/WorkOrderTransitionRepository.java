package com.grash.repository;

import com.grash.model.WorkOrderTransition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Read/append repository for the transition history. Intentionally exposes no custom update/delete methods.
 */
public interface WorkOrderTransitionRepository extends JpaRepository<WorkOrderTransition, Long> {

    @EntityGraph(attributePaths = {"actor"})
    List<WorkOrderTransition> findByWorkOrder_IdOrderByCreatedAtAscIdAsc(Long workOrderId);

    long countByWorkOrder_Id(Long workOrderId);
}
