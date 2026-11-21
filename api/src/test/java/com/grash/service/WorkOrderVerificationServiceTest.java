package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.TaskShowDTO;
import com.grash.dto.UserMiniDTO;
import com.grash.dto.workOrder.WorkOrderShowDTO;
import com.grash.dto.workOrder.WorkOrderVerificationDecisionDTO;
import com.grash.dto.workOrder.WorkOrderVerificationDetailDTO;
import com.grash.dto.workOrder.WorkOrderVerificationQueueItemDTO;
import com.grash.event.WorkOrderVerificationSubmittedEvent;
import com.grash.exception.CustomException;
import com.grash.mapper.TaskMapper;
import com.grash.mapper.UserMapper;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.*;
import com.grash.model.enums.Language;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Status;
import com.grash.model.enums.TaskType;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import com.grash.model.enums.WorkOrderVerificationDecision;
import com.grash.repository.WorkOrderRepository;
import com.grash.repository.WorkOrderTransitionRepository;
import com.grash.repository.WorkOrderVerificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderVerificationServiceTest {

    @Mock
    private WorkOrderVerificationRepository verificationRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private WorkOrderService workOrderService;
    @Mock
    private UserService userService;
    @Mock
    private TaskService taskService;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private LaborService laborService;
    @Mock
    private PartQuantityService partQuantityService;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MessageSource messageSource;
    @Mock
    private UserMapper userMapper;
    @Mock
    private WorkOrderMapper workOrderMapper;
    @Mock
    private EntityManager em;
    @Mock
    private WorkOrderTransitionRepository transitionRepository;

    private WorkOrderVerificationService service;

    private Company company;
    private Company otherCompany;
    private User technician;
    private User supervisor;
    private User otherSupervisor;
    private WorkOrder workOrder;

    @BeforeEach
    void setUp() {
        WorkOrderLifecycleService lifecycleService = new WorkOrderLifecycleService(transitionRepository,
                workOrderRepository);
        service = new WorkOrderVerificationService(verificationRepository, workOrderRepository, workOrderService,
                lifecycleService, userService, taskService, taskMapper, laborService, partQuantityService,
                dataScopeService, notificationService, messageSource, userMapper, workOrderMapper, em,
                new ObjectMapper());

        company = new Company();
        company.setId(10L);
        otherCompany = new Company();
        otherCompany.setId(20L);

        technician = user(100L, company, false);
        supervisor = user(200L, company, true);
        otherSupervisor = user(201L, company, true);

        workOrder = new WorkOrder();
        workOrder.setId(1L);
        workOrder.setCompany(company);
        workOrder.setTitle("Replace pump seal");
        workOrder.setLifecycleState(WorkOrderLifecycleState.VERIFICATION_PENDING);
        workOrder.setCompletedBy(technician);
        workOrder.setCompletedOn(new Date());
        workOrder.setAssignedTo(new ArrayList<>(List.of(technician)));

        lenient().when(workOrderService.findById(1L)).thenReturn(Optional.of(workOrder));
        lenient().when(workOrderService.checkAccessToWorkOrderId(eq(1L), any())).thenReturn(workOrder);
        lenient().when(workOrderService.saveAndFlushWithWebhook(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskService.findByWorkOrder(1L)).thenReturn(new ArrayList<>());
        lenient().when(laborService.findByWorkOrder(1L)).thenReturn(List.of());
        lenient().when(partQuantityService.findByWorkOrder(1L)).thenReturn(List.of());
        lenient().when(verificationRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());
        lenient().when(verificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transitionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(workOrderMapper.toShowDto(any())).thenReturn(new WorkOrderShowDTO());
        lenient().when(userMapper.toMiniDto(any())).thenReturn(new UserMiniDTO());
        lenient().when(messageSource.getMessage(anyString(), any(), any(Locale.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dataScopeService.isRestricted(any())).thenReturn(false);
    }

    private User user(Long id, Company company, boolean supervisor) {
        Role role = new Role();
        role.setId(id + 1);
        role.setCreatePermissions(new HashSet<>());
        role.setViewPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        role.setViewOtherPermissions(new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)));
        role.setEditOtherPermissions(supervisor ? new HashSet<>(Set.of(PermissionEntity.WORK_ORDERS)) :
                new HashSet<>());
        role.setDeleteOtherPermissions(new HashSet<>());
        User user = new User();
        user.setId(id);
        user.setCompany(company);
        user.setRole(role);
        user.setLanguage(Language.EN);
        user.setFirstName("First" + id);
        user.setLastName("Last" + id);
        user.setEnabled(true);
        return user;
    }

    private Task task(TaskType type, boolean required, String value) {
        TaskBase taskBase = new TaskBase();
        taskBase.setLabel("Checklist " + type);
        taskBase.setTaskType(type);
        taskBase.setRequired(required);
        Task task = new Task();
        task.setTaskBase(taskBase);
        task.setValue(value);
        return task;
    }

    private WorkOrderVerificationDecisionDTO decision(String reason) {
        WorkOrderVerificationDecisionDTO dto = new WorkOrderVerificationDecisionDTO();
        dto.setReason(reason);
        dto.setComments("comment");
        return dto;
    }

    // ------------------------------------------------------------------
    // Approval
    // ------------------------------------------------------------------

    @Test
    void approve_byAssignedSupervisor_closesWorkOrderAndRecordsDecision() {
        workOrder.setVerifier(supervisor);

        WorkOrderVerificationDetailDTO result = service.approve(1L, decision(null), supervisor, "ios");

        assertEquals(WorkOrderLifecycleState.CLOSED, workOrder.getLifecycleState());
        assertEquals(Status.COMPLETE, workOrder.getStatus());
        assertNotNull(workOrder.getClosedOn());
        assertSame(supervisor, workOrder.getClosedBy());
        assertSame(technician, workOrder.getCompletedBy());
        assertNotNull(result);

        ArgumentCaptor<WorkOrderVerification> captor = ArgumentCaptor.forClass(WorkOrderVerification.class);
        verify(verificationRepository, times(1)).save(captor.capture());
        assertEquals(WorkOrderVerificationDecision.APPROVED, captor.getValue().getDecision());
        assertSame(supervisor, captor.getValue().getVerifier());
        assertNotNull(captor.getValue().getEvidenceSnapshot());

        ArgumentCaptor<WorkOrderTransition> transitions = ArgumentCaptor.forClass(WorkOrderTransition.class);
        verify(transitionRepository, times(2)).save(transitions.capture());
        assertEquals(WorkOrderLifecycleState.VERIFICATION_PENDING,
                transitions.getAllValues().get(0).getPreviousState());
        assertEquals(WorkOrderLifecycleState.VERIFIED, transitions.getAllValues().get(0).getNewState());
        assertEquals(WorkOrderLifecycleState.VERIFIED,
                transitions.getAllValues().get(1).getPreviousState());
        assertEquals(WorkOrderLifecycleState.CLOSED, transitions.getAllValues().get(1).getNewState());

        verify(workOrderService).runOfficialClosure(workOrder, supervisor, "ios");
        verify(notificationService).createMultiple(anyList(), eq(false), anyString());
    }

    @Test
    void approve_withoutAssignedVerifier_isAllowedForAnySupervisor() {
        service.approve(1L, decision(null), otherSupervisor, null);

        assertEquals(WorkOrderLifecycleState.CLOSED, workOrder.getLifecycleState());
        assertSame(otherSupervisor, workOrder.getClosedBy());
    }

    @Test
    void approve_byTechnician_isForbidden() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), technician, null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void approve_byCompletingUser_isForbiddenEvenForSupervisor() {
        workOrder.setCompletedBy(supervisor);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("cannot verify their own"));
        assertEquals(WorkOrderLifecycleState.VERIFICATION_PENDING, workOrder.getLifecycleState());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void approve_bySupervisorOtherThanAssignedVerifier_isForbidden() {
        workOrder.setVerifier(supervisor);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), otherSupervisor, null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void approve_whenWorkOrderIsNotAwaitingVerification_isConflicting() {
        workOrder.setLifecycleState(WorkOrderLifecycleState.WORK_COMPLETED);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    @Test
    void approve_fromAnotherCompany_isForbidden() {
        User outsider = user(300L, otherCompany, true);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), outsider, null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void approve_outsideDataScope_isForbidden() {
        workOrder.setAsset(new Asset());
        when(dataScopeService.isRestricted(supervisor)).thenReturn(true);
        when(dataScopeService.canAccessAsset(eq(supervisor), any())).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void approve_withFailedMandatoryInspection_isBlocked() {
        when(taskService.findByWorkOrder(1L))
                .thenReturn(new ArrayList<>(List.of(task(TaskType.INSPECTION, true, "FAIL"))));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("failed"));
        assertEquals(WorkOrderLifecycleState.VERIFICATION_PENDING, workOrder.getLifecycleState());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void approve_withUnassessedMandatoryInspection_isBlocked() {
        when(taskService.findByWorkOrder(1L))
                .thenReturn(new ArrayList<>(List.of(task(TaskType.INSPECTION, true, "FLAG"))));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("PASS"));
    }

    @Test
    void approve_withIncompleteMandatorySubtask_isBlocked() {
        when(taskService.findByWorkOrder(1L))
                .thenReturn(new ArrayList<>(List.of(task(TaskType.SUBTASK, true, "IN_PROGRESS"))));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void approve_withEmptyMandatoryItem_isBlocked() {
        when(taskService.findByWorkOrder(1L))
                .thenReturn(new ArrayList<>(List.of(task(TaskType.NUMBER, true, " "))));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void approve_withCompletedMandatoryItems_isAllowed() {
        when(taskService.findByWorkOrder(1L)).thenReturn(new ArrayList<>(List.of(
                task(TaskType.INSPECTION, true, "PASS"),
                task(TaskType.SUBTASK, true, "COMPLETE"),
                task(TaskType.NUMBER, false, ""))));

        service.approve(1L, decision(null), supervisor, null);

        assertEquals(WorkOrderLifecycleState.CLOSED, workOrder.getLifecycleState());
    }

    @Test
    void approve_withMissingCompletionData_isBlocked() {
        workOrder.setCompletedOn(null);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.approve(1L, decision(null), supervisor, null));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("completion timestamp"));
    }

    // ------------------------------------------------------------------
    // Rejection and rework
    // ------------------------------------------------------------------

    @Test
    void reject_withoutReason_isRejected() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.reject(1L, decision("   "), supervisor));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertEquals("A rejection reason is required", exception.getMessage());
        verify(verificationRepository, never()).save(any());
    }

    @Test
    void reject_byTechnician_isForbidden() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.reject(1L, decision("Seal still leaking"), technician));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void reject_byCompletingSupervisor_isForbidden() {
        workOrder.setCompletedBy(supervisor);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.reject(1L, decision("Not done"), supervisor));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void reject_recordsDecisionAndReturnsWorkOrderForRework() {
        Date completedOn = workOrder.getCompletedOn();

        service.reject(1L, decision("Seal still leaking"), supervisor);

        assertEquals(WorkOrderLifecycleState.IN_PROGRESS, workOrder.getLifecycleState());
        assertEquals(Status.IN_PROGRESS, workOrder.getStatus());
        // Completion evidence is preserved for the next verification cycle.
        assertSame(technician, workOrder.getCompletedBy());
        assertEquals(completedOn, workOrder.getCompletedOn());

        ArgumentCaptor<WorkOrderVerification> captor = ArgumentCaptor.forClass(WorkOrderVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertEquals(WorkOrderVerificationDecision.REJECTED, captor.getValue().getDecision());
        assertEquals("Seal still leaking", captor.getValue().getRejectionReason());
        assertNotNull(captor.getValue().getEvidenceSnapshot());

        ArgumentCaptor<WorkOrderTransition> transition = ArgumentCaptor.forClass(WorkOrderTransition.class);
        verify(transitionRepository).save(transition.capture());
        assertEquals(WorkOrderLifecycleState.IN_PROGRESS, transition.getValue().getNewState());
        assertEquals(WorkOrderTransitionReasonCode.REWORK, transition.getValue().getReasonCode());
    }

    @Test
    void reject_thenResubmitThenApprove_appendsDecisionHistoryForEveryCycle() {
        service.reject(1L, decision("Seal still leaking"), supervisor);

        // Second cycle: technician resubmits and a supervisor approves.
        WorkOrder secondCycle = new WorkOrder();
        secondCycle.setId(2L);
        secondCycle.setCompany(company);
        secondCycle.setTitle("Replace pump seal");
        secondCycle.setLifecycleState(WorkOrderLifecycleState.VERIFICATION_PENDING);
        secondCycle.setCompletedBy(technician);
        secondCycle.setCompletedOn(new Date());
        when(workOrderService.findById(2L)).thenReturn(Optional.of(secondCycle));
        lenient().when(taskService.findByWorkOrder(2L)).thenReturn(new ArrayList<>());
        lenient().when(laborService.findByWorkOrder(2L)).thenReturn(List.of());
        lenient().when(partQuantityService.findByWorkOrder(2L)).thenReturn(List.of());
        lenient().when(verificationRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(2L))
                .thenReturn(List.of());

        service.approve(2L, decision(null), supervisor, null);

        ArgumentCaptor<WorkOrderVerification> captor = ArgumentCaptor.forClass(WorkOrderVerification.class);
        verify(verificationRepository, times(2)).save(captor.capture());
        assertEquals(WorkOrderVerificationDecision.REJECTED, captor.getAllValues().get(0).getDecision());
        assertEquals(WorkOrderVerificationDecision.APPROVED, captor.getAllValues().get(1).getDecision());
        assertEquals(WorkOrderLifecycleState.CLOSED, secondCycle.getLifecycleState());
        // Rejection never deletes or rewrites previously recorded decisions.
        verify(verificationRepository, never()).delete(any());
        verify(verificationRepository, never()).deleteById(anyLong());
    }

    // ------------------------------------------------------------------
    // Verifier assignment
    // ------------------------------------------------------------------

    @Test
    void assignVerifier_requiresSupervisor() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.assignVerifier(1L, supervisor.getId(), technician));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void assignVerifier_rejectsNonSupervisorTarget() {
        when(userService.findById(technician.getId())).thenReturn(Optional.of(technician));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.assignVerifier(1L, technician.getId(), supervisor));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void assignVerifier_rejectsCompletedBy() {
        workOrder.setCompletedBy(supervisor);
        when(userService.findById(supervisor.getId())).thenReturn(Optional.of(supervisor));

        CustomException exception = assertThrows(CustomException.class,
                () -> service.assignVerifier(1L, supervisor.getId(), otherSupervisor));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
        assertTrue(exception.getMessage().contains("cannot verify"));
    }

    @Test
    void assignVerifier_recordsPendingAssignmentAndNotifiesVerifier() {
        when(userService.findById(otherSupervisor.getId())).thenReturn(Optional.of(otherSupervisor));

        service.assignVerifier(1L, otherSupervisor.getId(), supervisor);

        assertSame(otherSupervisor, workOrder.getVerifier());
        ArgumentCaptor<WorkOrderVerification> captor = ArgumentCaptor.forClass(WorkOrderVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertEquals(WorkOrderVerificationDecision.PENDING, captor.getValue().getDecision());
        assertSame(otherSupervisor, captor.getValue().getVerifier());
        verify(notificationService).createMultiple(anyList(), eq(false), anyString());
    }

    @Test
    void assignVerifier_notificationUsesConfiguredMessageCodes() {
        when(userService.findById(otherSupervisor.getId())).thenReturn(Optional.of(otherSupervisor));

        service.assignVerifier(1L, otherSupervisor.getId(), supervisor);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> notifications = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notificationService).createMultiple(notifications.capture(), eq(false), title.capture());
        assertEquals("verification_assigned_content", notifications.getValue().get(0).getMessage());
        assertEquals("verification_assigned_title", title.getValue());
    }

    @Test
    void assignVerifier_whenWorkIsStillInProgress_isConflicting() {
        workOrder.setLifecycleState(WorkOrderLifecycleState.IN_PROGRESS);

        CustomException exception = assertThrows(CustomException.class,
                () -> service.assignVerifier(1L, supervisor.getId(), supervisor));
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    // ------------------------------------------------------------------
    // Queue, counts and submission
    // ------------------------------------------------------------------

    @Test
    void getQueue_mapsCompanyQueueAndFiltersOutsideScope() {
        when(workOrderRepository.findVerificationQueue(company.getId(),
                WorkOrderLifecycleState.VERIFICATION_PENDING))
                .thenReturn(List.of(workOrder));

        List<WorkOrderVerificationQueueItemDTO> queue = service.getQueue(supervisor, false);

        assertEquals(1, queue.size());
        assertEquals(1L, queue.get(0).getId());
        assertEquals("Replace pump seal", queue.get(0).getTitle());
        assertEquals(WorkOrderLifecycleState.VERIFICATION_PENDING, queue.get(0).getLifecycleState());
        assertEquals(1L, service.getCount(supervisor, false).getCount());
    }

    @Test
    void getQueue_excludesWorkOrdersOutsideSupervisorScope() {
        workOrder.setAsset(new Asset());
        when(workOrderRepository.findVerificationQueue(company.getId(),
                WorkOrderLifecycleState.VERIFICATION_PENDING))
                .thenReturn(List.of(workOrder));
        when(dataScopeService.isRestricted(supervisor)).thenReturn(true);
        when(dataScopeService.canAccessAsset(eq(supervisor), any())).thenReturn(false);

        assertTrue(service.getQueue(supervisor, false).isEmpty());
    }

    @Test
    void getQueue_requiresSupervisor() {
        CustomException exception = assertThrows(CustomException.class,
                () -> service.getQueue(technician, false));
        assertEquals(HttpStatus.FORBIDDEN, exception.getHttpStatus());
    }

    @Test
    void onSubmittedForVerification_recordsPendingAndNotifiesAssignedVerifier() {
        workOrder.setVerifier(supervisor);

        service.onSubmittedForVerification(workOrder, technician);

        ArgumentCaptor<WorkOrderVerification> captor = ArgumentCaptor.forClass(WorkOrderVerification.class);
        verify(verificationRepository).save(captor.capture());
        assertEquals(WorkOrderVerificationDecision.PENDING, captor.getValue().getDecision());
        assertSame(supervisor, captor.getValue().getVerifier());
        verify(notificationService).createMultiple(anyList(), eq(false), anyString());
    }

    @Test
    void onSubmittedForVerification_withoutVerifier_notifiesEnabledSupervisorsOnly() {
        when(userService.findWorkersByCompany(company.getId()))
                .thenReturn(List.of(technician, supervisor, otherSupervisor));

        service.onSubmittedForVerification(workOrder, technician);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationService).createMultiple(captor.capture(), eq(false), anyString());
        assertEquals(2, captor.getValue().size());
        assertTrue(captor.getValue().stream().allMatch(notification ->
                notification.getUser().getId() >= 200L));
    }

    @Test
    void submissionEvent_reloadsWorkOrderAndUser() {
        when(workOrderRepository.findById(1L)).thenReturn(Optional.of(workOrder));
        when(userService.findById(technician.getId())).thenReturn(Optional.of(technician));

        service.handleSubmittedForVerification(
                new WorkOrderVerificationSubmittedEvent(1L, technician.getId()));

        verify(verificationRepository).save(any());
    }

    @Test
    void getDetails_exposesChecklistBlockersAndPermissions() {
        when(taskService.findByWorkOrder(1L))
                .thenReturn(new ArrayList<>(List.of(task(TaskType.INSPECTION, true, "FAIL"))));

        WorkOrderVerificationDetailDTO details = service.getDetails(1L, supervisor);

        assertTrue(details.isAwaitingVerification());
        assertTrue(details.isCanDecide());
        assertTrue(details.isCanAssignVerifier());
        assertFalse(details.getBlockers().isEmpty());
        assertNotNull(details.getTasks());
    }

    @Test
    void getDetails_notAwaitingVerification_disablesDecisions() {
        workOrder.setLifecycleState(WorkOrderLifecycleState.IN_PROGRESS);

        WorkOrderVerificationDetailDTO details = service.getDetails(1L, supervisor);

        assertFalse(details.isAwaitingVerification());
        assertFalse(details.isCanDecide());
        assertFalse(details.isCanAssignVerifier());
    }

    @Test
    void getHistory_returnsPermanentDecisionRecords() {
        WorkOrderVerification record = WorkOrderVerification.builder()
                .id(5L)
                .workOrder(workOrder)
                .verifier(supervisor)
                .decision(WorkOrderVerificationDecision.REJECTED)
                .decidedAt(new Date())
                .rejectionReason("Leaking")
                .build();
        when(verificationRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(1L))
                .thenReturn(List.of(record));

        var history = service.getHistory(1L, supervisor);

        assertEquals(1, history.size());
        assertEquals(WorkOrderVerificationDecision.REJECTED, history.get(0).getDecision());
        assertEquals("Leaking", history.get(0).getRejectionReason());
    }
}
