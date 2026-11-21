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
  TextField,
  Typography,
  useTheme
} from '@mui/material';
import { useContext, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import WorkOrder from '../../../../models/owns/workOrder';
import type {
  WorkOrderAvailableTransition,
  WorkOrderLifecycleState,
  WorkOrderTransitionReasonCode
} from '../../../../models/owns/workOrder';
import {
  getWorkOrderLifecycle,
  getWorkOrderTransitionHistory,
  requestWorkOrderTransition
} from '../../../../slices/workOrder';
import { useDispatch, useSelector } from '../../../../store';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../utils/api';

interface WorkOrderLifecycleSectionProps {
  workOrder: WorkOrder;
}

const STATE_LABEL_KEY = (state: WorkOrderLifecycleState) =>
  `lifecycle_state_${state}`;

export default function WorkOrderLifecycleSection(
  props: WorkOrderLifecycleSectionProps
) {
  const { workOrder } = props;
  const { t } = useTranslation();
  const dispatch = useDispatch();
  const theme = useTheme();
  const { showSnackBar } = useContext(CustomSnackBarContext);

  const lifecycle = useSelector(
    (state) => state.workOrders.lifecycleByWorkOrder[workOrder.id]
  );
  const history = useSelector(
    (state) => state.workOrders.transitionsByWorkOrder[workOrder.id] ?? []
  );

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [target, setTarget] = useState<WorkOrderAvailableTransition | null>(
    null
  );
  const [reasonCode, setReasonCode] =
    useState<WorkOrderTransitionReasonCode>('NORMAL');
  const [reason, setReason] = useState('');

  const refresh = async () => {
    await Promise.all([
      dispatch(getWorkOrderLifecycle(workOrder.id)),
      dispatch(getWorkOrderTransitionHistory(workOrder.id))
    ]);
  };

  useEffect(() => {
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
  }, [workOrder.id]);

  const currentState: WorkOrderLifecycleState =
    lifecycle?.currentState ?? workOrder.lifecycleState ?? 'REPORTED';
  const availableTransitions = lifecycle?.availableTransitions ?? [];

  const stateColor = useMemo(() => {
    switch (currentState) {
      case 'IN_PROGRESS':
        return theme.colors.success.main;
      case 'WAITING_ON_HOLD':
        return theme.colors.warning.main;
      case 'WORK_COMPLETED':
      case 'VERIFICATION_PENDING':
        return theme.palette.info.main;
      case 'VERIFIED':
        return theme.palette.success.dark;
      case 'CLOSED':
        return theme.colors.alpha.black[30];
      default:
        return theme.palette.grey[600];
    }
  }, [currentState, theme]);

  const reasonRequired =
    !!target &&
    (target.reasonRequired ||
      (reasonCode !== 'NORMAL' && reasonCode !== 'LEGACY_STATUS_CHANGE'));

  const openTransitionDialog = (transition: WorkOrderAvailableTransition) => {
    const codes = transition.allowedReasonCodes ?? [];
    setTarget(transition);
    setReasonCode(codes.includes('NORMAL') ? 'NORMAL' : codes[0] ?? 'NORMAL');
    setReason('');
  };

  const closeDialog = () => {
    if (submitting) return;
    setTarget(null);
    setReason('');
  };

  const submitTransition = async () => {
    if (!target) return;
    if (reasonRequired && !reason.trim()) return;
    setSubmitting(true);
    try {
      await dispatch(
        requestWorkOrderTransition(workOrder.id, {
          targetState: target.targetState,
          reasonCode,
          reason: reason.trim() ? reason.trim() : undefined
        })
      );
      showSnackBar(t('lifecycle_transition_success'), 'success');
      setTarget(null);
      setReason('');
      await refresh();
    } catch (err) {
      showSnackBar(getErrorMessage(err), 'error');
    } finally {
      setSubmitting(false);
    }
  };

  const reasonCodes = target?.allowedReasonCodes ?? [];
  const closingAsCancelled =
    target?.targetState === 'CLOSED' && reasonCode === 'CANCELLED';

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
        <Typography variant="h6">{t('lifecycle_state')}</Typography>
        {loading ? (
          <CircularProgress size="1rem" />
        ) : (
          <Chip
            label={t(STATE_LABEL_KEY(currentState))}
            sx={{
              backgroundColor: stateColor,
              color: 'white',
              fontWeight: 'bold'
            }}
          />
        )}
      </Stack>

      {!loading && availableTransitions.length > 0 && (
        <Box sx={{ mb: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            {t('lifecycle_available_actions')}
          </Typography>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
            {availableTransitions.map((transition) => (
              <Button
                key={transition.targetState}
                variant={
                  transition.targetState === 'CLOSED'
                    ? 'outlined'
                    : 'contained'
                }
                color={transition.targetState === 'CLOSED' ? 'error' : 'primary'}
                size="small"
                onClick={() => openTransitionDialog(transition)}
              >
                {t(STATE_LABEL_KEY(transition.targetState))}
              </Button>
            ))}
          </Box>
        </Box>
      )}

      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {t('lifecycle_history')}
      </Typography>
      {loading ? (
        <CircularProgress size="1rem" />
      ) : history.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {t('lifecycle_history_empty')}
        </Typography>
      ) : (
        <List dense disablePadding>
          {[...history].reverse().map((transition) => {
            const actorName = transition.actor
              ? `${transition.actor.firstName} ${transition.actor.lastName}`
              : t('system');
            return (
              <ListItem key={transition.id} alignItems="flex-start" divider>
                <ListItemText
                  primary={`${t(
                    STATE_LABEL_KEY(transition.previousState)
                  )} → ${t(STATE_LABEL_KEY(transition.newState))}`}
                  secondary={
                    <>
                      <Typography
                        component="span"
                        variant="body2"
                        color="text.secondary"
                      >
                        {actorName} ·{' '}
                        {new Date(transition.createdAt).toLocaleString()}
                      </Typography>
                      {transition.reason && (
                        <Typography
                          component="span"
                          variant="body2"
                          color="text.secondary"
                          display="block"
                        >
                          {transition.reason}
                        </Typography>
                      )}
                    </>
                  }
                />
              </ListItem>
            );
          })}
        </List>
      )}

      <Dialog open={!!target} onClose={closeDialog} fullWidth maxWidth="xs">
        <DialogTitle>
          {t('lifecycle_confirm_title', {
            state: target ? t(STATE_LABEL_KEY(target.targetState)) : ''
          })}
        </DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t(`lifecycle_transition_hint_${target?.targetState ?? 'NONE'}`)}
          </Typography>
          {closingAsCancelled && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {t('lifecycle_cancel_warning')}
            </Alert>
          )}
          {reasonCodes.length > 1 && (
            <Select
              fullWidth
              size="small"
              value={reasonCode}
              onChange={(event) =>
                setReasonCode(
                  event.target.value as WorkOrderTransitionReasonCode
                )
              }
              sx={{ mb: 2 }}
            >
              {reasonCodes
                .filter((code) => code !== 'LEGACY_STATUS_CHANGE')
                .map((code) => (
                  <MenuItem key={code} value={code}>
                    {t(`lifecycle_reason_${code}`)}
                  </MenuItem>
                ))}
            </Select>
          )}
          <TextField
            fullWidth
            multiline
            minRows={2}
            size="small"
            label={t('lifecycle_reason')}
            required={reasonRequired}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDialog} disabled={submitting}>
            {t('cancel')}
          </Button>
          <Button
            variant="contained"
            onClick={submitTransition}
            disabled={
              submitting || (reasonRequired && !reason.trim())
            }
          >
            {submitting ? (
              <CircularProgress size="1rem" />
            ) : (
              t('confirm')
            )}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
