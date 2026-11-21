import {
  Box,
  Button,
  Card,
  Chip,
  CircularProgress,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getWorkOrderVerificationQueue
} from '../../../../slices/workOrder';
import { useDispatch, useSelector } from '../../../../store';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { getErrorMessage } from '../../../../utils/api';

interface VerificationQueueProps {
  handleOpenDetails: (id: number) => void;
}

export default function VerificationQueue(props: VerificationQueueProps) {
  const { handleOpenDetails } = props;
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const queue = useSelector((state) => state.workOrders.verificationQueue);

  const [loading, setLoading] = useState(true);
  const [assignedToMe, setAssignedToMe] = useState(false);

  const refresh = async (assigned: boolean) => {
    setLoading(true);
    try {
      await dispatch(getWorkOrderVerificationQueue(assigned));
    } catch (err) {
      showSnackBar(getErrorMessage(err), 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refresh(assignedToMe);
  }, [assignedToMe]);

  const userName = (user) =>
    user ? `${user.firstName} ${user.lastName}` : '-';

  return (
    <Card sx={{ p: 2 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        justifyContent="space-between"
        alignItems={{ xs: 'flex-start', sm: 'center' }}
        spacing={1}
        sx={{ mb: 2 }}
      >
        <Box>
          <Typography variant="h5">{t('verification_queue')}</Typography>
          <Typography variant="body2" color="text.secondary">
            {t('verification_count_outstanding')}: {queue.length}
          </Typography>
        </Box>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="body2">
            {t('verification_queue_assigned_to_me')}
          </Typography>
          <Switch
            checked={assignedToMe}
            onChange={(event) => setAssignedToMe(event.target.checked)}
          />
        </Stack>
      </Stack>

      {loading ? (
        <Box display="flex" justifyContent="center" sx={{ p: 4 }}>
          <CircularProgress />
        </Box>
      ) : queue.length === 0 ? (
        <Box sx={{ p: 4 }}>
          <Typography variant="body2" color="text.secondary" align="center">
            {t('verification_queue_empty')}
          </Typography>
        </Box>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('title')}</TableCell>
              <TableCell>{t('asset')}</TableCell>
              <TableCell>{t('location')}</TableCell>
              <TableCell>{t('verification_completed_by')}</TableCell>
              <TableCell>{t('verification_completed_on')}</TableCell>
              <TableCell>{t('verification_assigned_to')}</TableCell>
              <TableCell align="right">{t('actions')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {queue.map((item) => (
              <TableRow key={item.id} hover>
                <TableCell>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Typography variant="body2">
                      {item.title}
                    </Typography>
                    <Chip
                      size="small"
                      color="info"
                      label={t('verification_status_PENDING')}
                    />
                  </Stack>
                  {item.customId && (
                    <Typography variant="caption" color="text.secondary">
                      {item.customId}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{item.asset?.name ?? '-'}</TableCell>
                <TableCell>{item.location?.name ?? '-'}</TableCell>
                <TableCell>{userName(item.completedBy)}</TableCell>
                <TableCell>
                  {item.completedOn
                    ? new Date(item.completedOn).toLocaleString()
                    : '-'}
                </TableCell>
                <TableCell>
                  {item.verifier ? (
                    <Chip
                      size="small"
                      color="primary"
                      label={userName(item.verifier)}
                    />
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      {t('verification_no_verifier')}
                    </Typography>
                  )}
                </TableCell>
                <TableCell align="right">
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => handleOpenDetails(item.id)}
                  >
                    {t('verification_open')}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Card>
  );
}
