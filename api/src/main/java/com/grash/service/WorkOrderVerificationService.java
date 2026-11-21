package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.AssetMiniDTO;
import com.grash.dto.LocationMiniDTO;
import com.grash.dto.UserMiniDTO;
import com.grash.dto.workOrder.*;
import com.grash.event.WorkOrderVerificationSubmittedEvent;
import com.grash.exception.CustomException;
import com.grash.mapper.TaskMapper;
import com.grash.mapper.UserMapper;
import com.grash.mapper.WorkOrderMapper;
import com.grash.model.*;
import com.grash.model.enums.NotificationType;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.TaskType;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import com.grash.model.enums.WorkOrderVerificationDecision;
import com.grash.repository.WorkOrderRepository;
import com.grash.repository.WorkOrderVerificationRepository;
import com.grash.utils.Helper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.grash.model.enums.WorkOrderLifecycleState.VERIFICATION_PENDING;

/**
 * Supervisor verification workflow for Phase 4.
 * <p>
 * Verification records are append-only. Approval and rejection never write {@code lifecycleState}
 * directly: they validate and apply edges through {@link WorkOrderLifecycleService} so the lifecycle
 * history stays the single source of truth. Every public decision method re-checks tenant, scope,
 * role and separation-of-duties rules server-side.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderVerificationService {

    private final WorkOrderVerificationRepository verificationRepository;
    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderService workOrderService;
    private final WorkOrderLifecycleService lifecycleService;
    private final UserService userService;
    private final TaskService taskService;
    private final TaskMapper taskMapper;
    private final LaborService laborService;
    private final PartQuantityService partQuantityService;
    private final DataScopeService dataScopeService;
    private final NotificationService notificationService;
    private final org.springframework.context.MessageSource messageSource;
    private final UserMapper userMapper;
    private final WorkOrderMapper workOrderMapper;
    private final EntityManager em;
    private final ObjectMapper objectMapper;

    private static final Set<String> FAILED_INSPECTION_VALUES = Set.of("FAIL");
    private static final Set<String> INCOMPLETE_SUBTASK_VALUES = Set.of("OPEN", "IN_PROGRESS", "ON_HOLD");

    // ------------------------------------------------------------------
    // Queue and read APIs
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<WorkOrderVerificationQueueItemDTO> getQueue(User user, boolean assignedToMe) {
        assertSupervisor(user);
        Long companyId = user.getCompany().getId();
        List<WorkOrder> workOrders = assignedToMe
                ? workOrderRepository.findVerificationQueueForVerifier(companyId, VERIFICATION_PENDING,
                user.getId())
                : workOrderRepository.findVerificationQueue(companyId, VERIFICATION_PENDING);
        return workOrders.stream()
                .filter(workOrder -> canAccessScope(workOrder, user))
                .map(this::toQueueItem)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public WorkOrderVerificationCountDTO getCount(User user, boolean assignedToMe) {
        return new WorkOrderVerificationCountDTO(getQueue(user, assignedToMe).size());
    }

    @Transactional(readOnly = true)
    public WorkOrderVerificationDetailDTO getDetails(Long id, User user) {
        WorkOrder workOrder = workOrderService.checkAccessToWorkOrderId(id, user);
        assertSameCompany(workOrder, user);
        assertScope(workOrder, user);
        return buildDetails(workOrder, user, true);
    }

    @Transactional(readOnly = true)
    public List<WorkOrderVerificationHistoryDTO> getHistory(Long id, User user) {
        WorkOrder workOrder = workOrderService.checkAccessToWorkOrderId(id, user);
        assertSameCompany(workOrder, user);
        assertScope(workOrder, user);
        return verificationRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrder.getId()).stream()
                .map(this::toHistoryDto)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Verifier assignment
    // ------------------------------------------------------------------

    @Transactional
    public WorkOrderVerificationDetailDTO assignVerifier(Long id, Long verifierId, User user) {
        WorkOrder original = loadWorkOrder(id);
        em.detach(original);
        WorkOrder workOrder = loadWorkOrder(id);

        assertSupervisor(user);
        assertSameCompany(workOrder, user);
        assertScope(workOrder, user);
        WorkOrderLifecycleState state = lifecycleService.resolveCurrentState(workOrder);
        if (state != WorkOrderLifecycleState.WORK_COMPLETED && state != VERIFICATION_PENDING) {
            throw new CustomException("A verifier can only be assigned to completed work", HttpStatus.CONFLICT);
        }

        User verifier = null;
        if (verifierId != null) {
            verifier = userService.findById(verifierId)
                    .orElseThrow(() -> new CustomException("Verifier not found", HttpStatus.NOT_FOUND));
            assertSameCompany(workOrder, verifier);
            if (!verifier.isEnabled()) {
                throw new CustomException("The selected verifier is disabled", HttpStatus.BAD_REQUEST);
            }
            if (!isSupervisor(verifier)) {
                throw new CustomException("The selected verifier is not an authorized supervisor",
                        HttpStatus.BAD_REQUEST);
            }
            if (workOrder.getCompletedBy() != null
                    && Objects.equals(workOrder.getCompletedBy().getId(), verifier.getId())) {
                throw new CustomException("The technician who completed the work cannot verify it",
                        HttpStatus.BAD_REQUEST);
            }
            if (!canAccessScope(workOrder, verifier)) {
                throw new CustomException("The selected verifier does not have access to this work order's scope",
                        HttpStatus.BAD_REQUEST);
            }
        }

        workOrder.setVerifier(verifier);
        WorkOrder saved = workOrderService.saveAndFlushWithWebhook(workOrder, user.getCompany(), original);

        WorkOrderVerification assignment = WorkOrderVerification.builder()
                .workOrder(saved)
                .verifier(verifier)
                .decision(WorkOrderVerificationDecision.PENDING)
                .build();
        assignment.setCompany(saved.getCompany());
        verificationRepository.save(assignment);

        if (verifier != null) {
            notify(Collections.singletonList(verifier), "verification_assigned_content",
                    "verification_assigned_title",
                    new Object[]{saved.getTitle()}, saved.getId());
        }
        return buildDetails(saved, user, true);
    }

    // ------------------------------------------------------------------
    // Approval
    // ------------------------------------------------------------------

    @Transactional
    public WorkOrderVerificationDetailDTO approve(Long id, WorkOrderVerificationDecisionDTO dto, User user,
                                                  String platform) {
        WorkOrder original = loadWorkOrder(id);
        em.detach(original);
        WorkOrder workOrder = loadWorkOrder(id);
        assertCanDecide(workOrder, user);

        List<String> blockers = collectBlockers(workOrder);
        if (!blockers.isEmpty()) {
            throw new CustomException("Work order cannot be approved: " + String.join("; ", blockers),
                    HttpStatus.BAD_REQUEST);
        }

        List<Task> tasks = taskService.findByWorkOrder(workOrder.getId());
        saveDecision(workOrder, user, WorkOrderVerificationDecision.APPROVED, null,
                dto != null ? dto.getComments() : null, buildEvidenceSnapshot(workOrder, tasks));

        String reason = "Verification approved by " + user.getFullName();
        WorkOrderTransitionPlan toVerified = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.VERIFIED, WorkOrderTransitionReasonCode.NORMAL, reason, user);
        lifecycleService.applyTransition(workOrder, toVerified);

        WorkOrderTransitionPlan toClosed = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.CLOSED, WorkOrderTransitionReasonCode.NORMAL, reason, user);
        lifecycleService.applyTransition(workOrder, toClosed);

        workOrder.setClosedOn(new Date());
        workOrder.setClosedBy(user);

        WorkOrder saved = workOrderService.saveAndFlushWithWebhook(workOrder, user.getCompany(), original);
        lifecycleService.recordTransition(saved, toVerified, reason, null, user);
        lifecycleService.recordTransition(saved, toClosed, reason, null, user);
        workOrderService.runOfficialClosure(saved, user, platform);

        List<User> recipients = new ArrayList<>();
        if (saved.getCompletedBy() != null) {
            recipients.add(saved.getCompletedBy());
        }
        notify(recipients, "verification_approved_content", "verification_approved_title",
                new Object[]{saved.getTitle(), user.getFullName()}, saved.getId());
        return buildDetails(saved, user, true);
    }

    // ------------------------------------------------------------------
    // Rejection and rework
    // ------------------------------------------------------------------

    @Transactional
    public WorkOrderVerificationDetailDTO reject(Long id, WorkOrderVerificationDecisionDTO dto, User user) {
        String cleanReason = dto != null && dto.getReason() != null ? dto.getReason().trim() : "";
        if (cleanReason.isEmpty()) {
            throw new CustomException("A rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        if (cleanReason.length() > 2000) {
            throw new CustomException("Rejection reason is too long", HttpStatus.BAD_REQUEST);
        }

        WorkOrder original = loadWorkOrder(id);
        em.detach(original);
        WorkOrder workOrder = loadWorkOrder(id);
        assertCanDecide(workOrder, user);

        List<Task> tasks = taskService.findByWorkOrder(workOrder.getId());
        saveDecision(workOrder, user, WorkOrderVerificationDecision.REJECTED, cleanReason,
                dto.getComments(), buildEvidenceSnapshot(workOrder, tasks));

        WorkOrderTransitionPlan plan = lifecycleService.validateTransition(workOrder,
                WorkOrderLifecycleState.IN_PROGRESS, WorkOrderTransitionReasonCode.REWORK, cleanReason, user);
        lifecycleService.applyTransition(workOrder, plan);
        // Completion evidence is intentionally preserved on rejection; the verification record
        // permanently stores the snapshot reviewed by the supervisor.

        WorkOrder saved = workOrderService.saveAndFlushWithWebhook(workOrder, user.getCompany(), original);
        lifecycleService.recordTransition(saved, plan, cleanReason, null, user);

        List<User> recipients = new ArrayList<>();
        if (saved.getCompletedBy() != null) {
            recipients.add(saved.getCompletedBy());
        }
        recipients.addAll(saved.getAssignedTo());
        notify(recipients, "verification_rejected_content", "verification_rejected_title",
                new Object[]{saved.getTitle(), user.getFullName(), cleanReason}, saved.getId());
        return buildDetails(saved, user, true);
    }

    // ------------------------------------------------------------------
    // Submission hook used by WorkOrderService
    // ------------------------------------------------------------------

    /**
     * Records that a technician handed work over for verification and notifies the verifier (or every
     * authorized supervisor when no verifier is assigned). Called inside the lifecycle transaction via
     * {@link WorkOrderVerificationSubmittedEvent}.
     */
    @Transactional
    public void onSubmittedForVerification(WorkOrder workOrder, User submitter) {
        WorkOrderVerification pending = WorkOrderVerification.builder()
                .workOrder(workOrder)
                .verifier(workOrder.getVerifier())
                .decision(WorkOrderVerificationDecision.PENDING)
                .build();
        pending.setCompany(workOrder.getCompany() != null ? workOrder.getCompany() : submitter.getCompany());
        verificationRepository.save(pending);

        List<User> recipients;
        if (workOrder.getVerifier() != null) {
            recipients = Collections.singletonList(workOrder.getVerifier());
        } else {
            recipients = userService.findWorkersByCompany(submitter.getCompany().getId()).stream()
                    .filter(this::isSupervisor)
                    .filter(User::isEnabled)
                    .collect(Collectors.toList());
        }
        notify(recipients, "verification_submitted_content", "verification_submitted_title",
                new Object[]{workOrder.getTitle(), submitter.getFullName()}, workOrder.getId());
    }

    /**
     * Synchronous listener so the PENDING record is part of the same transaction as the
     * WORK_COMPLETED -> VERIFICATION_PENDING lifecycle edge.
     */
    @EventListener
    public void handleSubmittedForVerification(WorkOrderVerificationSubmittedEvent event) {
        WorkOrder workOrder = workOrderRepository.findById(event.getWorkOrderId()).orElse(null);
        User submitter = userService.findById(event.getSubmitterId()).orElse(null);
        if (workOrder == null || submitter == null) return;
        onSubmittedForVerification(workOrder, submitter);
    }

    // ------------------------------------------------------------------
    // Authorization and validation
    // ------------------------------------------------------------------

    private void assertCanDecide(WorkOrder workOrder, User user) {
        WorkOrderLifecycleState state = lifecycleService.resolveCurrentState(workOrder);
        if (state != VERIFICATION_PENDING) {
            throw new CustomException("Work order is not awaiting verification", HttpStatus.CONFLICT);
        }
        assertSupervisor(user);
        assertSameCompany(workOrder, user);
        assertScope(workOrder, user);
        if (workOrder.getVerifier() != null && !Objects.equals(workOrder.getVerifier().getId(), user.getId())) {
            throw new CustomException("This work order is assigned to another verifier", HttpStatus.FORBIDDEN);
        }
        if (workOrder.getCompletedBy() != null
                && Objects.equals(workOrder.getCompletedBy().getId(), user.getId())) {
            throw new CustomException("Technicians cannot verify their own completed work", HttpStatus.FORBIDDEN);
        }
    }

    /**
     * Collects every reason why approval must be blocked: missing completion data and incomplete or
     * failed mandatory checklist items. Non-mandatory items are visible in the detail but do not block.
     * <p>
     * Mandatory items follow the semantics already used by the checklist engine: subtasks must be
     * {@code COMPLETE}, inspections must produce {@code PASS} (the default {@code FLAG} and {@code FAIL}
     * both block) and free-form/number/meter/multiple choices must have a value.
     */
    public List<String> collectBlockers(WorkOrder workOrder) {
        List<String> blockers = new ArrayList<>();
        if (workOrder.getCompletedBy() == null) {
            blockers.add("the work order has no completion author");
        }
        if (workOrder.getCompletedOn() == null) {
            blockers.add("the work order has no completion timestamp");
        }
        for (Task task : taskService.findByWorkOrder(workOrder.getId())) {
            if (task.getTaskBase() == null || !task.getTaskBase().isRequired()) continue;
            String label = task.getTaskBase().getLabel();
            String value = task.getValue() == null ? "" : task.getValue().trim();
            TaskType type = task.getTaskBase().getTaskType();
            if (value.isEmpty()) {
                blockers.add("mandatory checklist item \"" + label + "\" has not been completed");
                continue;
            }
            if (type == TaskType.INSPECTION) {
                if (FAILED_INSPECTION_VALUES.contains(value)) {
                    blockers.add("mandatory safety check \"" + label + "\" failed");
                } else if (!"PASS".equals(value)) {
                    blockers.add("mandatory safety check \"" + label + "\" has no PASS result (current: "
                            + value + ")");
                }
            } else if (type == TaskType.SUBTASK && INCOMPLETE_SUBTASK_VALUES.contains(value)) {
                blockers.add("mandatory checklist item \"" + label + "\" is not complete (current: "
                        + value + ")");
            }
        }
        return blockers;
    }

    private boolean isSupervisor(User user) {
        return user != null && user.getRole() != null
                && user.getRole().getEditOtherPermissions() != null
                && user.getRole().getEditOtherPermissions().contains(PermissionEntity.WORK_ORDERS);
    }

    private void assertSupervisor(User user) {
        if (!isSupervisor(user)) {
            throw new CustomException("Only an authorized supervisor can verify work orders", HttpStatus.FORBIDDEN);
        }
    }

    private void assertSameCompany(WorkOrder workOrder, User user) {
        if (workOrder.getCompany() == null || user == null || user.getCompany() == null
                || !Objects.equals(workOrder.getCompany().getId(), user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private void assertScope(WorkOrder workOrder, User user) {
        if (!canAccessScope(workOrder, user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    private boolean canAccessScope(WorkOrder workOrder, User user) {
        if (user == null || user.getRole() == null) return false;
        if (!dataScopeService.isRestricted(user)) return true;
        if (workOrder.getAsset() != null) {
            return dataScopeService.canAccessAsset(user, workOrder.getAsset());
        }
        if (workOrder.getLocation() != null) {
            return dataScopeService.canAccessLocation(user, workOrder.getLocation());
        }
        // No facility binding exists on the work order; company-level access remains applicable.
        return true;
    }

    // ------------------------------------------------------------------
    // Mapping and evidence
    // ------------------------------------------------------------------

    private WorkOrder loadWorkOrder(Long id) {
        return workOrderService.findById(id).orElseThrow(() -> new CustomException("Work Order not found",
                HttpStatus.NOT_FOUND));
    }

    private void saveDecision(WorkOrder workOrder, User user, WorkOrderVerificationDecision decision,
                              String rejectionReason, String comments, String evidenceSnapshot) {
        WorkOrderVerification record = WorkOrderVerification.builder()
                .workOrder(workOrder)
                .verifier(user)
                .decision(decision)
                .decidedAt(new Date())
                .rejectionReason(rejectionReason)
                .comments(comments)
                .evidenceSnapshot(evidenceSnapshot)
                .build();
        record.setCompany(workOrder.getCompany());
        verificationRepository.save(record);
    }

    private String buildEvidenceSnapshot(WorkOrder workOrder, List<Task> tasks) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("capturedAt", new Date());
        snapshot.put("completedBy", workOrder.getCompletedBy() != null
                ? workOrder.getCompletedBy().getFullName() : null);
        snapshot.put("completedOn", workOrder.getCompletedOn());
        snapshot.put("feedback", workOrder.getFeedback());
        snapshot.put("signaturePresent", workOrder.getSignature() != null && !workOrder.getSignature().isEmpty());
        snapshot.put("tasks", tasks.stream().map(task -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", task.getId());
            row.put("label", task.getTaskBase() != null ? task.getTaskBase().getLabel() : null);
            row.put("value", task.getValue());
            row.put("required", task.getTaskBase() != null && task.getTaskBase().isRequired());
            row.put("notes", task.getNotes());
            row.put("images", task.getImages() == null ? 0 : task.getImages().size());
            return row;
        }).collect(Collectors.toList()));
        snapshot.put("files", workOrder.getFiles() == null ? List.of() : workOrder.getFiles().stream()
                .map(file -> Map.of("id", file.getId(), "name", file.getName() == null ? "" : file.getName()))
                .collect(Collectors.toList()));
        snapshot.put("labors", laborService.findByWorkOrder(workOrder.getId()).stream()
                .map(labor -> Map.of("id", labor.getId(), "duration", labor.getDuration(),
                        "hourlyRate", labor.getHourlyRate()))
                .collect(Collectors.toList()));
        snapshot.put("parts", partQuantityService.findByWorkOrder(workOrder.getId()).stream()
                .map(part -> Map.of("id", part.getId(), "quantity", part.getQuantity(),
                        "part", part.getPart() != null ? part.getPart().getName() : ""))
                .collect(Collectors.toList()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
    }

    private WorkOrderVerificationQueueItemDTO toQueueItem(WorkOrder workOrder) {
        WorkOrderVerificationQueueItemDTO dto = new WorkOrderVerificationQueueItemDTO();
        dto.setId(workOrder.getId());
        dto.setCustomId(workOrder.getCustomId());
        dto.setTitle(workOrder.getTitle());
        dto.setPriority(workOrder.getPriority());
        dto.setStatus(workOrder.getStatus());
        dto.setLifecycleState(lifecycleService.resolveCurrentState(workOrder));
        dto.setCompletedOn(workOrder.getCompletedOn());
        dto.setCompletedBy(workOrder.getCompletedBy() != null ? userMapper.toMiniDto(workOrder.getCompletedBy()) :
                null);
        dto.setVerifier(workOrder.getVerifier() != null ? userMapper.toMiniDto(workOrder.getVerifier()) : null);
        dto.setAsset(workOrder.getAsset() != null ? toAssetMini(workOrder.getAsset()) : null);
        dto.setLocation(workOrder.getLocation() != null ? toLocationMini(workOrder.getLocation()) : null);
        dto.setDueDate(workOrder.getDueDate());
        return dto;
    }

    private AssetMiniDTO toAssetMini(Asset asset) {
        AssetMiniDTO dto = new AssetMiniDTO();
        dto.setId(asset.getId());
        dto.setName(asset.getName());
        dto.setCustomId(asset.getCustomId());
        dto.setLocationId(asset.getLocation() != null ? asset.getLocation().getId() : null);
        dto.setParentId(asset.getParentAsset() != null ? asset.getParentAsset().getId() : null);
        return dto;
    }

    private LocationMiniDTO toLocationMini(Location location) {
        LocationMiniDTO dto = new LocationMiniDTO();
        dto.setId(location.getId());
        dto.setName(location.getName());
        dto.setAddress(location.getAddress());
        return dto;
    }

    private WorkOrderVerificationHistoryDTO toHistoryDto(WorkOrderVerification record) {
        return new WorkOrderVerificationHistoryDTO(
                record.getId(),
                record.getVerifier() != null ? userMapper.toMiniDto(record.getVerifier()) : null,
                record.getDecision(),
                record.getDecidedAt(),
                record.getRejectionReason(),
                record.getComments(),
                record.getCreatedAt());
    }

    private WorkOrderVerificationDetailDTO buildDetails(WorkOrder workOrder, User user, boolean includeChecklist) {
        WorkOrderVerificationDetailDTO dto = new WorkOrderVerificationDetailDTO();
        dto.setWorkOrder(workOrderMapper.toShowDto(workOrder));
        dto.setVerifier(workOrder.getVerifier() != null ? userMapper.toMiniDto(workOrder.getVerifier()) : null);

        if (includeChecklist) {
            dto.setTasks(taskService.findByWorkOrder(workOrder.getId()).stream()
                    .map(taskMapper::toShowDto).collect(Collectors.toList()));
        }
        dto.setLabors(laborService.findByWorkOrder(workOrder.getId()).stream().map(labor -> {
            WorkOrderVerificationLaborDTO laborDto = new WorkOrderVerificationLaborDTO();
            laborDto.setId(labor.getId());
            laborDto.setAssignedTo(labor.getAssignedTo() != null ? userMapper.toMiniDto(labor.getAssignedTo()) :
                    null);
            laborDto.setDuration(labor.getDuration());
            laborDto.setHourlyRate(labor.getHourlyRate());
            laborDto.setStartedAt(labor.getStartedAt());
            laborDto.setStatus(labor.getStatus());
            laborDto.setTimeCategory(labor.getTimeCategory() != null ? labor.getTimeCategory().getName() : null);
            return laborDto;
        }).collect(Collectors.toList()));
        dto.setParts(partQuantityService.findByWorkOrder(workOrder.getId()).stream().map(part -> {
            WorkOrderVerificationPartDTO partDto = new WorkOrderVerificationPartDTO();
            partDto.setId(part.getId());
            partDto.setName(part.getPart() != null ? part.getPart().getName() : null);
            partDto.setQuantity(part.getQuantity());
            partDto.setCost(part.getPart() != null ? part.getPart().getCost() : 0);
            return partDto;
        }).collect(Collectors.toList()));
        dto.setHistory(verificationRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrder.getId()).stream()
                .map(this::toHistoryDto).collect(Collectors.toList()));

        WorkOrderLifecycleState state = lifecycleService.resolveCurrentState(workOrder);
        dto.setAwaitingVerification(state == VERIFICATION_PENDING);
        dto.setCanDecide(state == VERIFICATION_PENDING && canDecide(workOrder, user));
        dto.setCanAssignVerifier((state == WorkOrderLifecycleState.WORK_COMPLETED || state == VERIFICATION_PENDING)
                && isSupervisor(user) && canAccessScope(workOrder, user)
                && Objects.equals(workOrder.getCompany().getId(), user.getCompany().getId()));
        dto.setBlockers(collectBlockers(workOrder));
        return dto;
    }

    private boolean canDecide(WorkOrder workOrder, User user) {
        return isSupervisor(user)
                && Objects.equals(workOrder.getCompany().getId(), user.getCompany() == null ? null :
                user.getCompany().getId())
                && canAccessScope(workOrder, user)
                && (workOrder.getVerifier() == null
                || Objects.equals(workOrder.getVerifier().getId(), user.getId()))
                && (workOrder.getCompletedBy() == null
                || !Objects.equals(workOrder.getCompletedBy().getId(), user.getId()));
    }

    // ------------------------------------------------------------------
    // In-app notifications
    // ------------------------------------------------------------------

    private void notify(Collection<User> recipients, String contentKey, String titleKey, Object[] args,
                        Long workOrderId) {
        Map<Long, User> unique = new LinkedHashMap<>();
        recipients.forEach(recipient -> {
            if (recipient != null && recipient.isEnabled()) {
                unique.putIfAbsent(recipient.getId(), recipient);
            }
        });
        if (unique.isEmpty()) return;
        String title = messageSource.getMessage(titleKey, null, Helper.getLocale(unique.values().iterator().next()));
        List<Notification> notifications = unique.values().stream()
                .map(recipient -> new Notification(
                        messageSource.getMessage(contentKey, args, Helper.getLocale(recipient)),
                        recipient, NotificationType.WORK_ORDER, workOrderId))
                .collect(Collectors.toList());
        notificationService.createMultiple(notifications, false, title);
    }
}
