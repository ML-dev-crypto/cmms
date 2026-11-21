package com.grash.service;

import com.grash.dto.workOrder.WorkOrderTransitionPlan;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkOrderTransition;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Status;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import com.grash.repository.WorkOrderRepository;
import com.grash.repository.WorkOrderTransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderLifecycleServiceTest {

    @InjectMocks
    private WorkOrderLifecycleService lifecycleService;

    @Mock
    private WorkOrderRepository workOrderRepository;

    @Mock
    private WorkOrderTransitionRepository workOrderTransitionRepository;

    private Company company;
    private User technician;
    private User supervisor;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setId(10L);

        Role technicianRole = new Role();
        technicianRole.setId(1L);
        technicianRole.setCreatePermissions(new HashSet<>());
        technicianRole.setViewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        technicianRole.setViewOtherPermissions(new HashSet<>());
        technicianRole.setEditOtherPermissions(new HashSet<>());
        technicianRole.setDeleteOtherPermissions(new HashSet<>());

        technician = new User();
        technician.setId(100L);
        technician.setCompany(company);
        technician.setRole(technicianRole);

        Role supervisorRole = new Role();
        supervisorRole.setId(2L);
        supervisorRole.setCreatePermissions(new HashSet<>());
        supervisorRole.setViewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        supervisorRole.setViewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        supervisorRole.setEditOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        supervisorRole.setDeleteOtherPermissions(new HashSet<>());

        supervisor = new User();
        supervisor.setId(200L);
        supervisor.setCompany(company);
        supervisor.setRole(supervisorRole);

        workOrder = new WorkOrder();
        workOrder.setId(50L);
        workOrder.setCompany(company);
        workOrder.setLifecycleState(WorkOrderLifecycleState.REPORTED);
        workOrder.setStatus(Status.OPEN);
        workOrder.setAssignedTo(List.of(technician));
    }

    // ------------------------------------------------------------------
    // Legacy status mapping
    // ------------------------------------------------------------------

    @Test
    void lifecycleStateMapsToLegacyStatus() {
        assertEquals(Status.OPEN, WorkOrderLifecycleState.REPORTED.toLegacyStatus());
        assertEquals(Status.OPEN, WorkOrderLifecycleState.TRIAGED.toLegacyStatus());
        assertEquals(Status.OPEN, WorkOrderLifecycleState.ASSIGNED.toLegacyStatus());
        assertEquals(Status.OPEN, WorkOrderLifecycleState.ACKNOWLEDGED.toLegacyStatus());
        assertEquals(Status.IN_PROGRESS, WorkOrderLifecycleState.IN_PROGRESS.toLegacyStatus());
        assertEquals(Status.ON_HOLD, WorkOrderLifecycleState.WAITING_ON_HOLD.toLegacyStatus());
        assertEquals(Status.COMPLETE, WorkOrderLifecycleState.WORK_COMPLETED.toLegacyStatus());
        assertEquals(Status.COMPLETE, WorkOrderLifecycleState.VERIFICATION_PENDING.toLegacyStatus());
        assertEquals(Status.COMPLETE, WorkOrderLifecycleState.VERIFIED.toLegacyStatus());
        assertEquals(Status.COMPLETE, WorkOrderLifecycleState.CLOSED.toLegacyStatus());
    }

    @Test
    void legacyStatusMapsToLifecycleState() {
        assertEquals(WorkOrderLifecycleState.REPORTED, WorkOrderLifecycleState.fromLegacyStatus(Status.OPEN));
        assertEquals(WorkOrderLifecycleState.IN_PROGRESS, WorkOrderLifecycleState.fromLegacyStatus(Status.IN_PROGRESS));
        assertEquals(WorkOrderLifecycleState.WAITING_ON_HOLD, WorkOrderLifecycleState.fromLegacyStatus(Status.ON_HOLD));
        assertEquals(WorkOrderLifecycleState.CLOSED, WorkOrderLifecycleState.fromLegacyStatus(Status.COMPLETE));
        assertEquals(WorkOrderLifecycleState.REPORTED, WorkOrderLifecycleState.fromLegacyStatus(null));
    }

    @Test
    void nullLifecycleStateResolvesToReported() {
        workOrder.setLifecycleState(null);
        assertEquals(WorkOrderLifecycleState.REPORTED, lifecycleService.resolveCurrentState(workOrder));
    }

    // ------------------------------------------------------------------
    // Transition matrix
    // ------------------------------------------------------------------

    @Test
    void everyPermittedTransitionIsAccepted() {
        for (WorkOrderLifecycleState from : WorkOrderLifecycleState.values()) {
            for (WorkOrderLifecycleState to : WorkOrderLifecycleState.values()) {
                WorkOrder wo = workOrderIn(from);
                boolean expected = CMMS_EXPECTED_ALLOWED.contains(from + "->" + to);
                if (!expected) continue;
                WorkOrderTransitionReasonCode code = firstAllowedCode(from, to);
                assertDoesNotThrow(() -> lifecycleService.validateTransition(wo, to, code,
                        "documented reason", supervisor), "Expected allowed: " + from + " -> " + to);
            }
        }
    }

    @Test
    void everyUnlistedTransitionIsRejected() {
        for (WorkOrderLifecycleState from : WorkOrderLifecycleState.values()) {
            for (WorkOrderLifecycleState to : WorkOrderLifecycleState.values()) {
                WorkOrder wo = workOrderIn(from);
                if (CMMS_EXPECTED_ALLOWED.contains(from + "->" + to)) continue;
                CustomException ex = assertThrows(CustomException.class,
                        () -> lifecycleService.validateTransition(wo, to,
                                WorkOrderTransitionReasonCode.NORMAL, "reason", supervisor),
                        "Expected rejected: " + from + " -> " + to);
                assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
            }
        }
    }

    @Test
    void arbitraryJumpIsRejected() {
        CustomException ex = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrder, WorkOrderLifecycleState.IN_PROGRESS,
                        WorkOrderTransitionReasonCode.NORMAL, null, supervisor));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    // ------------------------------------------------------------------
    // Validation of reasons and codes
    // ------------------------------------------------------------------

    @Test
    void cancellationRequiresCancelledReasonCodeAndReason() {
        CustomException noCode = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrder, WorkOrderLifecycleState.CLOSED,
                        null, "patient transferred", supervisor));
        assertEquals(HttpStatus.BAD_REQUEST, noCode.getHttpStatus());

        CustomException noReason = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrder, WorkOrderLifecycleState.CLOSED,
                        WorkOrderTransitionReasonCode.CANCELLED, "  ", supervisor));
        assertEquals(HttpStatus.BAD_REQUEST, noReason.getHttpStatus());

        WorkOrderTransitionPlan plan = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.CLOSED, WorkOrderTransitionReasonCode.CANCELLED,
                "duplicate request", supervisor);
        assertEquals(WorkOrderLifecycleState.CLOSED, plan.targetState());
        assertEquals(WorkOrderTransitionReasonCode.CANCELLED, plan.reasonCode());
    }

    @Test
    void backwardTransitionsRequireReason() {
        WorkOrder wo = workOrderIn(WorkOrderLifecycleState.TRIAGED);
        CustomException ex = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(wo, WorkOrderLifecycleState.REPORTED,
                        WorkOrderTransitionReasonCode.RETURNED, " ", supervisor));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getHttpStatus());

        assertDoesNotThrow(() -> lifecycleService.validateTransition(wo,
                WorkOrderLifecycleState.REPORTED, WorkOrderTransitionReasonCode.RETURNED,
                "insufficient information", supervisor));
    }

    // ------------------------------------------------------------------
    // Authorization
    // ------------------------------------------------------------------

    @Test
    void normalTransitionsAllowedForAssignee() {
        // technician is assigned to the work order
        assertDoesNotThrow(() -> lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.TRIAGED, WorkOrderTransitionReasonCode.NORMAL, null, technician));
    }

    @Test
    void supervisoryTransitionsRejectedForNonSupervisor() {
        WorkOrder vo = workOrderIn(WorkOrderLifecycleState.VERIFICATION_PENDING);
        CustomException ex = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(vo, WorkOrderLifecycleState.VERIFIED,
                        WorkOrderTransitionReasonCode.NORMAL, null, technician));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());

        assertDoesNotThrow(() -> lifecycleService.validateTransition(vo,
                WorkOrderLifecycleState.VERIFIED, WorkOrderTransitionReasonCode.NORMAL, null, supervisor));
    }

    @Test
    void cancellationAfterWorkStartedRequiresSupervisor() {
        WorkOrder started = workOrderIn(WorkOrderLifecycleState.IN_PROGRESS);
        CustomException ex = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(started, WorkOrderLifecycleState.CLOSED,
                        WorkOrderTransitionReasonCode.CANCELLED, "stop work", technician));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());

        assertDoesNotThrow(() -> lifecycleService.validateTransition(started,
                WorkOrderLifecycleState.CLOSED, WorkOrderTransitionReasonCode.CANCELLED,
                "stop work", supervisor));
    }

    @Test
    void reopenFromClosedRequiresSupervisor() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrderIn(WorkOrderLifecycleState.CLOSED),
                        WorkOrderLifecycleState.REPORTED, WorkOrderTransitionReasonCode.REOPENED,
                        "recurred", technician)).getHttpStatus());
    }

    @Test
    void tenantMismatchIsRejected() {
        Company other = new Company();
        other.setId(99L);
        User foreignUser = new User();
        foreignUser.setId(300L);
        foreignUser.setCompany(other);
        foreignUser.setRole(supervisor.getRole());

        CustomException ex = assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrder, WorkOrderLifecycleState.TRIAGED,
                        WorkOrderTransitionReasonCode.NORMAL, null, foreignUser));
        assertEquals(HttpStatus.FORBIDDEN, ex.getHttpStatus());
    }

    // ------------------------------------------------------------------
    // Applying and recording
    // ------------------------------------------------------------------

    @Test
    void applyTransitionKeepsLegacyStatusInSync() {
        WorkOrderTransitionPlan plan = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.TRIAGED, WorkOrderTransitionReasonCode.NORMAL, null, supervisor);
        lifecycleService.applyTransition(workOrder, plan);

        assertEquals(WorkOrderLifecycleState.TRIAGED, workOrder.getLifecycleState());
        assertEquals(Status.OPEN, workOrder.getStatus());
    }

    @Test
    void technicianCompletionSyncsToCompleteButDoesNotClose() {
        WorkOrder wo = workOrderIn(WorkOrderLifecycleState.IN_PROGRESS);
        WorkOrderTransitionPlan plan = lifecycleService.validateTransition(wo,
                WorkOrderLifecycleState.WORK_COMPLETED, WorkOrderTransitionReasonCode.NORMAL, null, technician);
        lifecycleService.applyTransition(wo, plan);

        assertEquals(WorkOrderLifecycleState.WORK_COMPLETED, wo.getLifecycleState());
        assertEquals(Status.COMPLETE, wo.getStatus());
    }

    @Test
    void recordTransitionSetsActorReasonAndStates() {
        when(workOrderTransitionRepository.save(any(WorkOrderTransition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        WorkOrderTransitionPlan plan = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.TRIAGED, WorkOrderTransitionReasonCode.NORMAL,
                "triaged by phone", supervisor);

        WorkOrderTransition saved = lifecycleService.recordTransition(workOrder, plan,
                "triaged by phone", "{\"source\":\"test\"}", supervisor);

        assertEquals(WorkOrderLifecycleState.REPORTED, saved.getPreviousState());
        assertEquals(WorkOrderLifecycleState.TRIAGED, saved.getNewState());
        assertEquals(WorkOrderTransitionReasonCode.NORMAL, saved.getReasonCode());
        assertEquals("triaged by phone", saved.getReason());
        assertSame(supervisor, saved.getActor());
        assertSame(workOrder, saved.getWorkOrder());
        assertSame(company, saved.getCompany());
        assertEquals("{\"source\":\"test\"}", saved.getMetadata());
    }

    @Test
    void rejectedTransitionDoesNotCreateHistory() {
        assertThrows(CustomException.class, () ->
                lifecycleService.validateTransition(workOrder, WorkOrderLifecycleState.CLOSED,
                        WorkOrderTransitionReasonCode.NORMAL, "nope", supervisor));
        verifyNoInteractions(workOrderTransitionRepository);
        verifyNoInteractions(workOrderRepository);
    }

    @Test
    void availableTransitionsAreFilteredByPermission() {
        WorkOrder verification = workOrderIn(WorkOrderLifecycleState.VERIFICATION_PENDING);
        List<WorkOrderLifecycleState> technicianOptions =
                lifecycleService.getAvailableTransitions(verification, technician);
        assertEquals(List.of(WorkOrderLifecycleState.IN_PROGRESS), technicianOptions);

        List<WorkOrderLifecycleState> supervisorOptions =
                lifecycleService.getAvailableTransitions(verification, supervisor);
        assertEquals(List.of(WorkOrderLifecycleState.VERIFIED, WorkOrderLifecycleState.IN_PROGRESS),
                supervisorOptions);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private WorkOrder workOrderIn(WorkOrderLifecycleState state) {
        WorkOrder wo = new WorkOrder();
        wo.setId(50L);
        wo.setCompany(company);
        wo.setLifecycleState(state);
        wo.setStatus(state.toLegacyStatus());
        wo.setAssignedTo(List.of(technician));
        return wo;
    }

    private WorkOrderTransitionReasonCode firstAllowedCode(WorkOrderLifecycleState from,
                                                            WorkOrderLifecycleState to) {
        if (to == WorkOrderLifecycleState.CLOSED && from != WorkOrderLifecycleState.VERIFIED)
            return WorkOrderTransitionReasonCode.CANCELLED;
        if (from == WorkOrderLifecycleState.TRIAGED && to == WorkOrderLifecycleState.REPORTED)
            return WorkOrderTransitionReasonCode.RETURNED;
        if ((from == WorkOrderLifecycleState.ASSIGNED && to == WorkOrderLifecycleState.TRIAGED)
                || (from == WorkOrderLifecycleState.ACKNOWLEDGED && to == WorkOrderLifecycleState.ASSIGNED))
            return WorkOrderTransitionReasonCode.REASSIGNMENT;
        if (from == WorkOrderLifecycleState.WAITING_ON_HOLD)
            return WorkOrderTransitionReasonCode.RESUMED;
        if ((from == WorkOrderLifecycleState.WORK_COMPLETED
                || from == WorkOrderLifecycleState.VERIFICATION_PENDING
                || from == WorkOrderLifecycleState.VERIFIED) && to == WorkOrderLifecycleState.IN_PROGRESS)
            return WorkOrderTransitionReasonCode.REWORK;
        if (from == WorkOrderLifecycleState.CLOSED)
            return WorkOrderTransitionReasonCode.REOPENED;
        return WorkOrderTransitionReasonCode.NORMAL;
    }

    private static final Set<String> CMMS_EXPECTED_ALLOWED = Set.of(
            "REPORTED->TRIAGED",
            "REPORTED->CLOSED",
            "TRIAGED->ASSIGNED",
            "TRIAGED->REPORTED",
            "TRIAGED->CLOSED",
            "ASSIGNED->ACKNOWLEDGED",
            "ASSIGNED->TRIAGED",
            "ASSIGNED->CLOSED",
            "ACKNOWLEDGED->IN_PROGRESS",
            "ACKNOWLEDGED->ASSIGNED",
            "ACKNOWLEDGED->CLOSED",
            "IN_PROGRESS->WAITING_ON_HOLD",
            "IN_PROGRESS->WORK_COMPLETED",
            "IN_PROGRESS->CLOSED",
            "WAITING_ON_HOLD->IN_PROGRESS",
            "WAITING_ON_HOLD->CLOSED",
            "WORK_COMPLETED->VERIFICATION_PENDING",
            "WORK_COMPLETED->IN_PROGRESS",
            "VERIFICATION_PENDING->VERIFIED",
            "VERIFICATION_PENDING->IN_PROGRESS",
            "VERIFIED->CLOSED",
            "VERIFIED->IN_PROGRESS",
            "CLOSED->REPORTED"
    );
}
