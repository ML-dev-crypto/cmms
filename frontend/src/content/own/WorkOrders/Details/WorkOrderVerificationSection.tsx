import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
  useTheme
} from '@mui/material';
import { useContext, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import WorkOrder from '../../../../models/owns/workOrder';
import type {
  WorkOrderLifecycleState,
  WorkOrderVerificationDecision
} from '../../../../models/owns/workOrder';
import {
  approveWorkOrderVerification,
  assignWorkOrderVerifier,
  getWorkOrderVerification,
  rejectWorkOrderVerification
} from '../../../../slices/workOrder';
import { getUsersMini } from '../../../../slices/user';
import { useDispatch, useSelector } from '../../../../store';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../utils/api';
import { Task } from '../../../../models/owns/tasks';

interface WorkOrderVerificationSectionProps {
  workOrder: WorkOrder;
}

const VERIFICATION_RELEVANT_STATES: WorkOrderLifecycleState[] = [
  'WORK_COMPLETED',
  'VERIFICATION_PENDING',
  'VERIFIED',
  'CLOSED'
];

export default function WorkOrderVerificationSection(
  props: WorkOrderVerificationSectionProps
) {
  const { workOrder } = props;
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const theme = useTheme();
  const { showSnackBar } = useContext(CustomSnackBarContext);

  const verification = useSelector(
    (state) => state.workOrders.verificationByWorkOrder[workOrder.id]
  );
  const usersMini = useSelector((state) => state.users.usersMini);

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [approveComments, setApproveComments] = useState('');
  const [rejectReason, setRejectReason] = useState('');
  const [rejectComments, setRejectComments] = useState('');

  const currentState: WorkOrderLifecycleState =
    workOrder.lifecycleState ?? 'REPORTED';

  const shouldRender =
    VERIFICATION_RELEVANT_STATES.includes(currentState) ||
    (verification?.history?.length ?? 0) > 0;

  const refresh = async () => {
    await dispatch(getWorkOrderVerification(workOrder.id));
  };

  useEffect(() => {
    if (!shouldRender) {
      setLoading(false);
      return;
    }
    let active = true;
    setLoading(true);
    refresh()
      .catch((err) => {
        if (active) showSnackBar(getErrorMessage(err), 'error');
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [workOrder.id, currentState]);

  useEffect(() => {
    if (usersMini.length === 0) dispatch(getUsersMini());
  }, []);

  const latestDecision: WorkOrderVerificationDecision | undefined = useMemo(
    () =>
      verification?.history?.length
        ? verification.history[verification.history.length - 1].decision
        : undefined,
    [verification]
  );

  if (!shouldRender) return null;

  const statusColor = (decision: WorkOrderVerificationDecision) => {
    switch (decision) {
      case 'APPROVED':
        return theme.colors.success.main;
      case 'REJECTED':
        return theme.palette.error.main;
      default:
        return theme.palette.info.main;
    }
  };

  const statusLabel = verification?.awaitingVerification
    ? t('verification_status_PENDING')
    : latestDecision
    ? t(`verification_status_${latestDecision}`)
    : '';

  const statusDecision: WorkOrderVerificationDecision =
    verification?.awaitingVerification ? 'PENDING' : latestDecision ?? 'PENDING';

  const valueChipColor = (task: Task) => {
    const value = String(task.value ?? '').toUpperCase();
    if (task.taskBase?.taskType === 'INSPECTION') {
      if (value === 'FAIL') return 'error' as const;
      if (value === 'FLAG') return 'warning' as const;
      if (value === 'PASS') return 'success' as const;
    }
    if (task.taskBase?.taskType === 'SUBTASK') {
      if (value === 'COMPLETE') return 'success' as const;
      if (value === 'ON_HOLD') return 'warning' as const;
      if (value === 'IN_PROGRESS') return 'info' as const;
      return 'default' as const;
    }
    return 'default' as const;
  };

  const runDecision = async (
    action: () => Promise<unknown>,
    successMessage: string
  ) => {
    setSubmitting(true);
    try {
      await action();
      showSnackBar(successMessage, 'success');
      setApproveOpen(false);
      setRejectOpen(false);
      setApproveComments('');
      setRejectReason('');
      setRejectComments('');
      await refresh();
    } catch (err) {
      showSnackBar(getErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const submitApprove = () =>
    runDecision(
      () =>
        dispatch(
          approveWorkOrderVerification(workOrder.id, {
            comments: approveComments.trim() || undefined
          })
        ),
      t('verification_approved_success')
    );

  const submitReject = () => {
    if (!rejectReason.trim()) return;
    runDecision(
      () =>
        dispatch(
          rejectWorkOrderVerification(workOrder.id, {
            reason: rejectReason.trim(),
            comments: rejectComments.trim() || undefined
          })
        ),
      t('verification_rejected_success')
    );
  };

  const assignVerifier = async (verifierId: number | null) => {
    setSubmitting(true);
    try {
      await dispatch(assignWorkOrderVerifier(workOrder.id, verifierId));
      showSnackBar(t('save'), 'success');
    } catch (err) {
      showSnackBar(getErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const verifierName = verification?.verifier
    ? `${verification.verifier.firstName} ${verification.verifier.lastName}`
    : undefined;

  const completedByName = workOrder.completedBy
    ? `${workOrder.completedBy.firstName} ${workOrder.completedBy.lastName}`
    : t('not_available');

  return (
    <Box sx={{ width: '100%' }}>
      <Divider sx={{ my: 2 }} />
      <Stack
        direction="row"
        alignItems="center"
        justifyContent="space-between"
        spacing={2}
        sx={{ mb: 1.5 }}
      >
        <Typography variant="h6">{t('verification_section')}</Typography>
        {loading ? (
          <CircularProgress size="1rem" />
        ) : (
          statusLabel && (
            <Chip
              label={statusLabel}
              sx={{
                backgroundColor: statusColor(statusDecision),
                color: 'white',
                fontWeight: 'bold'
              }}
            />
          )
        )}
      </Stack>

      {!loading && verification?.awaitingVerification && (
        <Alert
          severity={verification.blockers?.length ? 'warning' : 'info'}
          sx={{ mb: 2 }}
        >
          <Typography variant="body2">{t('verification_awaiting')}</Typography>
          {(verification.blockers?.length ?? 0) > 0 && (
            <Box component="ul" sx={{ mt: 0.5, mb: 0, pl: 2 }}>
              {verification.blockers.map((blocker) => (
                <li key={blocker}>
                  <Typography variant="body2">{blocker}</Typography>
                </li>
              ))}
            </Box>
          )}
        </Alert>
      )}

      {!loading && (
        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('verification_assigned_to')}
          </Typography>
          {verification?.canAssignVerifier ? (
            <Stack direction="row" spacing={1} alignItems="center">
              <Select
                size="small"
                value={verification.verifier?.id ?? ''}
                displayEmpty
                disabled={submitting}
                sx={{ minWidth: 220 }}
                onChange={(event) =>
                  assignVerifier(
                    event.target.value === ''
                      ? null
                      : Number(event.target.value)
                  )
                }
              >
                <MenuItem value="">
                  <em>{t('verification_no_verifier')}</em>
                </MenuItem>
                {usersMini.map((user) => (
                  <MenuItem key={user.id} value={user.id}>
                    {`${user.firstName} ${user.lastName}`}
                  </MenuItem>
                ))}
              </Select>
              {verifierName && (
                <Chip size="small" label={verifierName} color="primary" />
              )}
            </Stack>
          ) : (
            <Typography variant="body2" color="text.secondary">
              {verifierName ?? t('verification_no_verifier')}
            </Typography>
          )}
        </Box>
      )}

      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {t('verification_completion_summary')}
      </Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ mb: 2 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">
            {t('verification_completed_by')}
          </Typography>
          <Typography variant="body2">{completedByName}</Typography>
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">
            {t('verification_completed_on')}
          </Typography>
          <Typography variant="body2">
            {workOrder.completedOn
              ? new Date(workOrder.completedOn).toLocaleString()
              : t('not_available')}
          </Typography>
        </Box>
      </Stack>
      {workOrder.feedback && (
        <Box sx={{ mb: 2 }}>
          <Typography variant="caption" color="text.secondary">
            {t('verification_feedback')}
          </Typography>
          <Typography variant="body2">{workOrder.feedback}</Typography>
        </Box>
      )}

      {verification?.tasks?.length > 0 && (
        <>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('verification_checklist')}
          </Typography>
          <List dense disablePadding sx={{ mb: 2 }}>
            {verification.tasks.map((task) => (
              <ListItem key={task.id} divider alignItems="flex-start">
                <ListItemText
                  primary={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2">
                        {task.taskBase?.label}
                      </Typography>
                      {task.taskBase?.required && (
                        <Chip
                          size="small"
                          color="secondary"
                          label={t('verification_required')}
                        />
                      )}
                      <Chip
                        size="small"
                        color={valueChipColor(task)}
                        label={String(task.value ?? '-')}
                      />
                    </Stack>
                  }
                  secondary={
                    <>
                      {task.notes && (
                        <Typography
                          component="span"
                          variant="body2"
                          color="text.secondary"
                          display="block"
                        >
                          {task.notes}
                        </Typography>
                      )}
                      {task.images?.length > 0 && (
                        <Typography
                          component="span"
                          variant="caption"
                          color="text.secondary"
                        >
                          {task.images.length} {t('image')}
                        </Typography>
                      )}
                    </>
                  }
                />
              </ListItem>
            ))}
          </List>
        </>
      )}

      {workOrder.files?.length > 0 && (
        <>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('verification_attachments')}
          </Typography>
          <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mb: 2 }}>
            {workOrder.files.map((file) => (
              <Button
                key={file.id}
                size="small"
                variant="outlined"
                href={file.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                {file.name}
              </Button>
            ))}
          </Stack>
        </>
      )}

      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {t('verification_labor')}
      </Typography>
      {verification?.labors?.length ? (
        <Table size="small" sx={{ mb: 2 }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('assigned_to')}</TableCell>
              <TableCell>{t('time_category')}</TableCell>
              <TableCell align="right">{t('duration')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {verification.labors.map((labor) => (
              <TableRow key={labor.id}>
                <TableCell>
                  {labor.assignedTo
                    ? `${labor.assignedTo.firstName} ${labor.assignedTo.lastName}`
                    : '-'}
                </TableCell>
                <TableCell>{labor.timeCategory ?? '-'}</TableCell>
                <TableCell align="right">{labor.duration}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('verification_no_labor')}
        </Typography>
      )}

      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {t('verification_parts')}
      </Typography>
      {verification?.parts?.length ? (
        <Table size="small" sx={{ mb: 2 }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('part')}</TableCell>
              <TableCell align="right">{t('quantity')}</TableCell>
              <TableCell align="right">{t('cost')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {verification.parts.map((part) => (
              <TableRow key={part.id}>
                <TableCell>{part.name ?? '-'}</TableCell>
                <TableCell align="right">{part.quantity}</TableCell>
                <TableCell align="right">{part.cost}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('verification_no_parts')}
        </Typography>
      )}

      {verification?.awaitingVerification && (
        <Box sx={{ mb: 2 }}>
          {verification.canDecide ? (
            <Stack direction="row" spacing={1}>
              <Button
                variant="contained"
                color="success"
                disabled={submitting}
                onClick={() => setApproveOpen(true)}
              >
                {t('verification_approve')}
              </Button>
              <Button
                variant="outlined"
                color="error"
                disabled={submitting}
                onClick={() => setRejectOpen(true)}
              >
                {t('verification_reject')}
              </Button>
            </Stack>
          ) : (
            <Alert severity="info">{t('verification_cannot_decide')}</Alert>
          )}
        </Box>
      )}

      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {t('verification_history')}
      </Typography>
      {!verification?.history?.length ? (
        <Typography variant="body2" color="text.secondary">
          {t('verification_history_empty')}
        </Typography>
      ) : (
        <List dense disablePadding>
          {[...verification.history].reverse().map((entry) => (
            <ListItem key={entry.id} alignItems="flex-start" divider>
              <ListItemText
                primary={
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip
                      size="small"
                      label={t(`verification_status_${entry.decision}`)}
                      sx={{
                        backgroundColor: statusColor(entry.decision),
                        color: 'white'
                      }}
                    />
                    <Typography variant="body2">
                      {entry.verifier
                        ? `${entry.verifier.firstName} ${entry.verifier.lastName}`
                        : t('system')}
                    </Typography>
                  </Stack>
                }
                secondary={
                  <>
                    <Typography
                      component="span"
                      variant="caption"
                      color="text.secondary"
                      display="block"
                    >
                      {entry.decidedAt || entry.createdAt
                        ? new Date(
                            entry.decidedAt ?? entry.createdAt
                          ).toLocaleString()
                        : ''}
                    </Typography>
                    {entry.rejectionReason && (
                      <Typography
                        component="span"
                        variant="body2"
                        color="error"
                        display="block"
                      >
                        {entry.rejectionReason}
                      </Typography>
                    )}
                    {entry.comments && (
                      <Typography
                        component="span"
                        variant="body2"
                        color="text.secondary"
                        display="block"
                      >
                        {entry.comments}
                      </Typography>
                    )}
                  </>
                }
              />
            </ListItem>
          ))}
        </List>
      )}

      <Dialog
        open={approveOpen}
        onClose={() => !submitting && setApproveOpen(false)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>{t('verification_approve_confirm_title')}</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('verification_approve_confirm_body')}
          </Typography>
          <TextField
            fullWidth
            multiline
            minRows={2}
            size="small"
            label={t('verification_comments')}
            value={approveComments}
            onChange={(event) => setApproveComments(event.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button
            onClick={() => setApproveOpen(false)}
            disabled={submitting}
          >
            {t('cancel')}
          </Button>
          <Button
            variant="contained"
            color="success"
            onClick={submitApprove}
            disabled={submitting}
          >
            {submitting ? <CircularProgress size="1rem" /> : t('confirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={rejectOpen}
        onClose={() => !submitting && setRejectOpen(false)}
        fullWidth
        maxWidth="xs"
      >
        <DialogTitle>{t('verification_reject_title')}</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            minRows={2}
            size="small"
            required
            label={t('verification_reject_reason')}
            value={rejectReason}
            onChange={(event) => setRejectReason(event.target.value)}
            error={!rejectReason.trim()}
            helperText={
              !rejectReason.trim() && t('verification_reject_reason_required')
            }
            sx={{ mt: 1, mb: 2 }}
          />
          <TextField
            fullWidth
            multiline
            minRows={2}
            size="small"
            label={t('verification_comments')}
            value={rejectComments}
            onChange={(event) => setRejectComments(event.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRejectOpen(false)} disabled={submitting}>
            {t('cancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={submitReject}
            disabled={submitting || !rejectReason.trim()}
          >
            {submitting ? <CircularProgress size="1rem" /> : t('confirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
