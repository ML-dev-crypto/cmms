package com.grash.service;

import com.grash.dto.workOrder.WorkOrderAvailableTransitionDTO;
import com.grash.dto.workOrder.WorkOrderTransitionPlan;
import com.grash.exception.CustomException;
import com.grash.model.User;
import com.grash.model.WorkOrder;
import com.grash.model.WorkOrderTransition;
import com.grash.model.enums.PermissionEntity;
import com.grash.model.enums.Status;
import com.grash.model.enums.WorkOrderLifecycleState;
import com.grash.model.enums.WorkOrderTransitionReasonCode;
import com.grash.repository.WorkOrderTransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.grash.model.enums.WorkOrderLifecycleState.*;
import static com.grash.model.enums.WorkOrderTransitionReasonCode.*;

/**
 * Central authority for work order lifecycle movement. No controller, mapper or frontend may write
 * {@code lifecycleState} directly; every state change must be produced by {@link #validateTransition} and
 * recorded through {@link #recordTransition} so that the legacy {@link Status} stays in sync and the
 * append-only transition history is complete.
 */
@Service
@RequiredArgsConstructor
public class WorkOrderLifecycleService {

    private final WorkOrderTransitionRepository workOrderTransitionRepository;
    private final com.grash.repository.WorkOrderRepository workOrderRepository;

    /** A single legal edge in the transition graph. */
    private record TransitionRule(WorkOrderLifecycleState target,
                                  Set<WorkOrderTransitionReasonCode> reasonCodes,
                                  boolean reasonRequired) {
        boolean allows(WorkOrderTransitionReasonCode code) {
            return reasonCodes.contains(code);
        }
    }

    private static final Map<WorkOrderLifecycleState, List<TransitionRule>> MATRIX = buildMatrix();

    private static Map<WorkOrderLifecycleState, List<TransitionRule>> buildMatrix() {
        Map<WorkOrderLifecycleState, List<TransitionRule>> matrix = new EnumMap<>(WorkOrderLifecycleState.class);

        matrix.put(REPORTED, List.of(
                rule(TRIAGED, false, NORMAL),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(TRIAGED, List.of(
                rule(ASSIGNED, false, NORMAL),
                rule(REPORTED, true, RETURNED),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(ASSIGNED, List.of(
                rule(ACKNOWLEDGED, false, NORMAL),
                rule(TRIAGED, true, REASSIGNMENT),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(ACKNOWLEDGED, List.of(
                rule(IN_PROGRESS, false, NORMAL),
                rule(ASSIGNED, true, REASSIGNMENT),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(IN_PROGRESS, List.of(
                rule(WAITING_ON_HOLD, false, NORMAL),
                rule(WORK_COMPLETED, false, NORMAL),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(WAITING_ON_HOLD, List.of(
                rule(IN_PROGRESS, true, RESUMED),
                rule(CLOSED, true, CANCELLED)));

        matrix.put(WORK_COMPLETED, List.of(
                rule(VERIFICATION_PENDING, false, NORMAL),
                rule(IN_PROGRESS, true, REWORK)));

        matrix.put(VERIFICATION_PENDING, List.of(
                rule(VERIFIED, false, NORMAL),
                rule(IN_PROGRESS, true, REWORK)));

        matrix.put(VERIFIED, List.of(
                rule(CLOSED, false, NORMAL),
                rule(IN_PROGRESS, true, REWORK)));

        matrix.put(CLOSED, List.of(
                rule(REPORTED, true, REOPENED)));

        return Collections.unmodifiableMap(matrix);
    }

    private static TransitionRule rule(WorkOrderLifecycleState target, boolean reasonRequired,
                                       WorkOrderTransitionReasonCode... codes) {
        Set<WorkOrderTransitionReasonCode> allowed = EnumSet.noneOf(WorkOrderTransitionReasonCode.class);
        allowed.addAll(Arrays.asList(codes));
        // Every edge can also be traversed by the legacy status adapter with an explicit synthetic reason.
        allowed.add(LEGACY_STATUS_CHANGE);
        return new TransitionRule(target, Collections.unmodifiableSet(allowed), reasonRequired);
    }

    // ------------------------------------------------------------------
    // Read helpers
    // ------------------------------------------------------------------

    public WorkOrderLifecycleState resolveCurrentState(WorkOrder workOrder) {
        if (workOrder == null) return REPORTED;
        return workOrder.getLifecycleState() != null ? workOrder.getLifecycleState() : REPORTED;
    }

    public List<WorkOrderLifecycleState> getAvailableTransitions(WorkOrder workOrder, User user) {
        return getAvailableTransitionOptions(workOrder, user).stream()
                .map(WorkOrderAvailableTransitionDTO::getTargetState)
                .collect(Collectors.toList());
    }

    public List<WorkOrderAvailableTransitionDTO> getAvailableTransitionOptions(WorkOrder workOrder, User user) {
        WorkOrderLifecycleState current = resolveCurrentState(workOrder);
        List<WorkOrderAvailableTransitionDTO> options = new ArrayList<>();
        for (TransitionRule rule : MATRIX.getOrDefault(current, List.of())) {
            WorkOrderTransitionReasonCode primaryCode = rule.reasonCodes().stream()
                    .filter(code -> code != LEGACY_STATUS_CHANGE)
                    .findFirst()
                    .orElse(NORMAL);
            if (!isAuthorized(workOrder, current, rule.target(), primaryCode, user)) continue;
            options.add(new WorkOrderAvailableTransitionDTO(rule.target(),
                    rule.reasonRequired(),
                    rule.reasonCodes().stream()
                            .filter(code -> code != LEGACY_STATUS_CHANGE)
                            .collect(Collectors.toList()),
                    requiresSupervisory(current, rule.target(), primaryCode)));
        }
        return options;
    }

    public List<WorkOrderTransition> getHistory(WorkOrder workOrder) {
        return workOrderTransitionRepository.findByWorkOrder_IdOrderByCreatedAtAscIdAsc(workOrder.getId());
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    public WorkOrderTransitionPlan validateTransition(WorkOrder workOrder,
                                                      WorkOrderLifecycleState targetState,
                                                      WorkOrderTransitionReasonCode reasonCode,
                                                      String reason,
                                                      User user) {
        if (workOrder == null) {
            throw new CustomException("Work order is required", HttpStatus.BAD_REQUEST);
        }
        if (targetState == null) {
            throw new CustomException("Target lifecycle state is required", HttpStatus.BAD_REQUEST);
        }
        if (user == null) {
            throw new CustomException("Authentication is required", HttpStatus.FORBIDDEN);
        }
        assertSameCompany(workOrder, user);

        WorkOrderLifecycleState current = resolveCurrentState(workOrder);
        if (current == targetState) {
            throw new CustomException("Work order is already in state " + targetState, HttpStatus.CONFLICT);
        }

        TransitionRule rule = MATRIX.getOrDefault(current, List.of()).stream()
                .filter(candidate -> candidate.target() == targetState)
                .findFirst()
                .orElseThrow(() -> new CustomException(
                        "Transition from " + current + " to " + targetState + " is not allowed",
                        HttpStatus.CONFLICT));

        WorkOrderTransitionReasonCode code = reasonCode != null ? reasonCode : NORMAL;
        if (!rule.allows(code)) {
            throw new CustomException("Reason code " + code + " is not allowed for transition "
                    + current + " to " + targetState, HttpStatus.BAD_REQUEST);
        }
        if (code == CANCELLED && targetState != CLOSED) {
            throw new CustomException("Cancellation must target CLOSED", HttpStatus.BAD_REQUEST);
        }

        String cleanReason = reason == null ? null : reason.trim();
        if ((rule.reasonRequired() || code != NORMAL) && (cleanReason == null || cleanReason.isEmpty())) {
            throw new CustomException("A reason is required for this transition", HttpStatus.BAD_REQUEST);
        }

        boolean supervisory = requiresSupervisory(current, targetState, code);
        if (supervisory) {
            if (!isSupervisor(user)) {
                throw new CustomException("Only a supervisor can perform this transition", HttpStatus.FORBIDDEN);
            }
        } else if (!workOrder.canBeEditedBy(user)) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }

        return new WorkOrderTransitionPlan(current, targetState, code, cleanReason, supervisory);
    }

    private boolean requiresSupervisory(WorkOrderLifecycleState current,
                                        WorkOrderLifecycleState target,
                                        WorkOrderTransitionReasonCode code) {
        if (target == VERIFIED) return true;
        if (current == VERIFIED && target == CLOSED) return true;
        if (current == CLOSED) return true; // reopening
        return code == CANCELLED && current.isWorkStarted();
    }

    private boolean isAuthorized(WorkOrder workOrder, WorkOrderLifecycleState current,
                                 WorkOrderLifecycleState target, WorkOrderTransitionReasonCode code, User user) {
        if (requiresSupervisory(current, target, code)) return isSupervisor(user);
        return workOrder.canBeEditedBy(user);
    }

    private boolean isSupervisor(User user) {
        return user != null && user.getRole() != null
                && user.getRole().getEditOtherPermissions() != null
                && user.getRole().getEditOtherPermissions().contains(PermissionEntity.WORK_ORDERS);
    }

    private void assertSameCompany(WorkOrder workOrder, User user) {
        if (workOrder.getCompany() != null && user.getCompany() != null
                && !Objects.equals(workOrder.getCompany().getId(), user.getCompany().getId())) {
            throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
        }
    }

    // ------------------------------------------------------------------
    // Apply / record
    // ------------------------------------------------------------------

    public void applyTransition(WorkOrder workOrder, WorkOrderTransitionPlan plan) {
        workOrder.setLifecycleState(plan.targetState());
        workOrder.setStatus(plan.targetState().toLegacyStatus());
    }

    public WorkOrderTransition recordTransition(WorkOrder workOrder, WorkOrderTransitionPlan plan,
                                                String reason, String metadata, User user) {
        String effectiveReason = reason != null && !reason.trim().isEmpty()
                ? reason.trim() : plan.reason();
        WorkOrderTransition transition = WorkOrderTransition.builder()
                .workOrder(workOrder)
                .previousState(plan.previousState())
                .newState(plan.targetState())
                .reasonCode(plan.reasonCode())
                .reason(effectiveReason)
                .actor(user)
                .metadata(metadata)
                .build();
        transition.setCompany(workOrder.getCompany() != null ? workOrder.getCompany() : user.getCompany());
        return workOrderTransitionRepository.save(transition);
    }

    // ------------------------------------------------------------------
    // Legacy Status adapter support
    // ------------------------------------------------------------------

    /**
     * Builds the sequence of validated plans needed to satisfy a legacy {@code /change-status} call.
     * The desired lifecycle target never bypasses the matrix: intermediate legal edges are traversed and
     * each one is recorded, so no arbitrary jump can be smuggled in through the legacy endpoint.
     * COMPLETE maps to WORK_COMPLETED (technician completion), never to CLOSED.
     */
    public List<WorkOrderTransitionPlan> planLegacyStatusChange(WorkOrder workOrder, Status legacyStatus,
                                                                User user) {
        if (legacyStatus == null) {
            throw new CustomException("Status can't be null", HttpStatus.NOT_ACCEPTABLE);
        }
        WorkOrderLifecycleState current = resolveCurrentState(workOrder);
        if (current.toLegacyStatus() == legacyStatus) {
            return List.of();
        }
        WorkOrderLifecycleState target = switch (legacyStatus) {
            case OPEN -> REPORTED;
            case IN_PROGRESS -> IN_PROGRESS;
            case ON_HOLD -> WAITING_ON_HOLD;
            case COMPLETE -> WORK_COMPLETED;
        };
        List<WorkOrderLifecycleState> path = findPath(current, target);
        if (path.isEmpty()) {
            throw new CustomException("Cannot apply legacy status " + legacyStatus + " from lifecycle state "
                    + current, HttpStatus.CONFLICT);
        }
        List<WorkOrderTransitionPlan> plans = new ArrayList<>();
        WorkOrderLifecycleState cursor = current;
        for (WorkOrderLifecycleState step : path) {
            WorkOrderLifecycleState stepFrom = cursor;
            TransitionRule rule = MATRIX.get(stepFrom).stream()
                    .filter(candidate -> candidate.target() == step)
                    .findFirst()
                    .orElseThrow(() -> new CustomException("No rule for legacy step " + stepFrom + " -> " + step,
                            HttpStatus.CONFLICT));
            String reason = "Legacy status change from " + stepFrom + " to " + step;
            WorkOrderTransitionPlan plan = new WorkOrderTransitionPlan(stepFrom, step, LEGACY_STATUS_CHANGE,
                    reason, requiresSupervisory(stepFrom, step, LEGACY_STATUS_CHANGE));
            if (plan.supervisory() && !isSupervisor(user)) {
                throw new CustomException("Only a supervisor can perform this transition", HttpStatus.FORBIDDEN);
            }
            if (!plan.supervisory() && !workOrder.canBeEditedBy(user)) {
                throw new CustomException("Access denied", HttpStatus.FORBIDDEN);
            }
            plans.add(plan);
            cursor = step;
        }
        return plans;
    }

    /**
     * Breadth-first search over legal non-cancellation edges. Used only by the legacy adapter.
     */
    private List<WorkOrderLifecycleState> findPath(WorkOrderLifecycleState from, WorkOrderLifecycleState to) {
        if (from == to) return List.of();
        Map<WorkOrderLifecycleState, WorkOrderLifecycleState> parents = new EnumMap<>(WorkOrderLifecycleState.class);
        Deque<WorkOrderLifecycleState> queue = new ArrayDeque<>();
        Set<WorkOrderLifecycleState> visited = EnumSet.of(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            WorkOrderLifecycleState state = queue.poll();
            for (TransitionRule rule : MATRIX.getOrDefault(state, List.of())) {
                if (rule.target() == CLOSED) continue; // legacy status changes never cancel/close
                if (!visited.add(rule.target())) continue;
                parents.put(rule.target(), state);
                if (rule.target() == to) {
                    LinkedList<WorkOrderLifecycleState> path = new LinkedList<>();
                    WorkOrderLifecycleState cursor = to;
                    while (cursor != from) {
                        path.addFirst(cursor);
                        cursor = parents.get(cursor);
                    }
                    return path;
                }
                queue.add(rule.target());
            }
        }
        return List.of();
    }
}
