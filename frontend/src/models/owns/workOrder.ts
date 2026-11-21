import { Audit } from './audit';
import Request from './request';
import Team from './team';
import Asset from './asset';
import File from './file';
import Location from './location';
import Category from './category';
import { OwnUser, UserMiniDTO } from '../user';
import { CustomerMiniDTO } from './customer';
import PreventiveMaintenance from './preventiveMaintenance';
import { WorkOrderBase } from './workOrderBase';
import { CustomField } from './customField';
import { Task } from './tasks';

export type Priority = 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE';

export type WorkOrderLifecycleState =
  | 'REPORTED'
  | 'TRIAGED'
  | 'ASSIGNED'
  | 'ACKNOWLEDGED'
  | 'IN_PROGRESS'
  | 'WAITING_ON_HOLD'
  | 'WORK_COMPLETED'
  | 'VERIFICATION_PENDING'
  | 'VERIFIED'
  | 'CLOSED';

export type WorkOrderTransitionReasonCode =
  | 'NORMAL'
  | 'CANCELLED'
  | 'REOPENED'
  | 'REWORK'
  | 'REASSIGNMENT'
  | 'RETURNED'
  | 'RESUMED'
  | 'LEGACY_STATUS_CHANGE';

export interface WorkOrderAvailableTransition {
  targetState: WorkOrderLifecycleState;
  reasonRequired: boolean;
  allowedReasonCodes: WorkOrderTransitionReasonCode[];
  supervisory: boolean;
}

export interface WorkOrderLifecycle {
  currentState: WorkOrderLifecycleState;
  status: string;
  availableTransitions: WorkOrderAvailableTransition[];
}

export interface WorkOrderTransition {
  id: number;
  workOrderId: number;
  previousState: WorkOrderLifecycleState;
  newState: WorkOrderLifecycleState;
  actor?: UserMiniDTO;
  reasonCode: WorkOrderTransitionReasonCode;
  reason?: string;
  metadata?: string;
  createdAt: string;
}

export interface WorkOrderTransitionRequest {
  targetState: WorkOrderLifecycleState;
  reasonCode?: WorkOrderTransitionReasonCode;
  reason?: string;
  metadata?: string;
  signature?: string;
  feedback?: string;
}

export type WorkOrderVerificationDecision =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED';

export interface WorkOrderVerificationHistory {
  id: number;
  verifier?: UserMiniDTO;
  decision: WorkOrderVerificationDecision;
  decidedAt?: string;
  rejectionReason?: string;
  comments?: string;
  createdAt: string;
}

export interface WorkOrderVerificationLabor {
  id: number;
  assignedTo?: UserMiniDTO;
  duration: number;
  hourlyRate: number;
  startedAt?: string;
  status?: string;
  timeCategory?: string;
}

export interface WorkOrderVerificationPart {
  id: number;
  name?: string;
  quantity: number;
  cost: number;
}

export interface WorkOrderVerificationDetail {
  workOrder: WorkOrder;
  verifier?: UserMiniDTO;
  tasks: Task[];
  labors: WorkOrderVerificationLabor[];
  parts: WorkOrderVerificationPart[];
  history: WorkOrderVerificationHistory[];
  blockers: string[];
  awaitingVerification: boolean;
  canDecide: boolean;
  canAssignVerifier: boolean;
}

export interface WorkOrderVerificationQueueItem {
  id: number;
  customId?: string;
  title: string;
  priority?: Priority;
  status?: string;
  lifecycleState?: WorkOrderLifecycleState;
  completedOn?: string;
  completedBy?: UserMiniDTO;
  verifier?: UserMiniDTO;
  asset?: { id: number; name: string };
  location?: { id: number; name: string };
  dueDate?: string;
}

export interface WorkOrderVerificationDecisionRequest {
  reason?: string;
  comments?: string;
}

export default interface WorkOrder extends WorkOrderBase {
  category: Category | null;
  id: number;
  completedBy: OwnUser;
  completedOn: string;
  archived: boolean;
  parentRequest: Request;
  parentPreventiveMaintenance: PreventiveMaintenance;
  signature: string;
  feedback: string;
  requiredSignature: boolean;
  status: string;
  lifecycleState?: WorkOrderLifecycleState;
  audioDescription: File;
  customId: string;
  //parentPreventiveMaintenance:
}

export interface WorkOrderMini {
  id: number;
  title: string;
  status: string;
  createdAt: string;
}
