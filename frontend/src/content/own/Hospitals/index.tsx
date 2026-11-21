import { Helmet } from 'react-helmet-async';
import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography
} from '@mui/material';
import AddTwoToneIcon from '@mui/icons-material/AddTwoTone';
import DeleteTwoToneIcon from '@mui/icons-material/DeleteTwoTone';
import EditTwoToneIcon from '@mui/icons-material/EditTwoTone';
import * as Yup from 'yup';
import { useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useDispatch, useSelector } from '../../../store';
import {
  addHospital,
  deleteHospital,
  editHospital,
  getHospitals
} from '../../../slices/hospital';
import Form from '../components/form';
import ConfirmDialog from '../components/ConfirmDialog';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { IField } from '../type';
import Hospital from '../../../models/owns/hospital';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { TitleContext } from '../../../contexts/TitleContext';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';

function Hospitals() {
  const { t }: { t: any } = useTranslation();
  const { setTitle } = useContext(TitleContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const dispatch = useDispatch();
  const { hospitals, loadingGet } = useSelector((state) => state.hospitals);
  const { hasViewPermission, hasCreatePermission, hasEditPermission, hasDeletePermission } =
    useAuth();
  const [openAddModal, setOpenAddModal] = useState(false);
  const [currentHospital, setCurrentHospital] = useState<Hospital | null>(null);
  const [hospitalToDelete, setHospitalToDelete] = useState<Hospital | null>(
    null
  );
  const [openDelete, setOpenDelete] = useState(false);

  useEffect(() => {
    setTitle(t('hospitals'));
    if (hasViewPermission(PermissionEntity.HOSPITALS_AND_DEPARTMENTS)) {
      dispatch(getHospitals());
    }
  }, []);

  const fields: Array<IField> = [
    {
      name: 'name',
      type: 'text',
      label: t('name'),
      placeholder: t('hospital_name'),
      required: true
    },
    {
      name: 'code',
      type: 'text',
      label: t('code'),
      placeholder: t('hospital_code'),
      midWidth: true
    },
    {
      name: 'phone',
      type: 'text',
      label: t('phone'),
      placeholder: t('phone'),
      midWidth: true
    },
    {
      name: 'email',
      type: 'text',
      label: t('email'),
      placeholder: t('email'),
      midWidth: true
    },
    {
      name: 'timezone',
      type: 'text',
      label: t('timezone'),
      placeholder: t('timezone'),
      midWidth: true
    },
    {
      name: 'address',
      type: 'text',
      label: t('address'),
      placeholder: t('address')
    },
    {
      name: 'description',
      type: 'text',
      label: t('description'),
      placeholder: t('description'),
      multiple: true
    },
    {
      name: 'active',
      type: 'checkbox',
      label: t('active')
    }
  ];

  const shape = {
    name: Yup.string().required(t('required_hospital_name'))
  };

  const onSaveSuccess = () => {
    setOpenAddModal(false);
    setCurrentHospital(null);
    showSnackBar(t('changes_saved_success'), 'success');
  };
  const onSaveFailure = () => showSnackBar(t('hospital_save_failure'), 'error');
  const onDeleteSuccess = () => {
    setOpenDelete(false);
    setHospitalToDelete(null);
    showSnackBar(t('hospital_remove_success'), 'success');
  };
  const onDeleteFailure = () =>
    showSnackBar(t('hospital_remove_failure'), 'error');

  const renderModal = () => (
    <Dialog
      fullWidth
      maxWidth="md"
      open={openAddModal || !!currentHospital}
      onClose={() => {
        setOpenAddModal(false);
        setCurrentHospital(null);
      }}
    >
      <DialogTitle sx={{ p: 3 }}>
        <Typography variant="h4" gutterBottom>
          {currentHospital ? t('edit_hospital') : t('add_hospital')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers sx={{ p: 3 }}>
        <Form
          fields={fields}
          validation={Yup.object().shape(shape)}
          submitText={currentHospital ? t('save') : t('create')}
          values={
            currentHospital
              ? {
                  ...currentHospital,
                  active: currentHospital.active ?? true
                }
              : { active: true }
          }
          onSubmit={async (values) => {
            try {
              if (currentHospital) {
                await dispatch(editHospital(currentHospital.id, values));
              } else {
                await dispatch(addHospital(values));
              }
              onSaveSuccess();
            } catch (err) {
              onSaveFailure();
              throw err;
            }
          }}
        />
      </DialogContent>
    </Dialog>
  );

  if (!hasViewPermission(PermissionEntity.HOSPITALS_AND_DEPARTMENTS))
    return <PermissionErrorMessage message={'no_access_hospitals'} />;

  return (
    <>
      <Helmet>
        <title>{t('hospitals')}</title>
      </Helmet>
      <Box paddingX={4} paddingY={2}>
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          mb={2}
        >
          <Typography variant="h3">{t('hospitals')}</Typography>
          {hasCreatePermission(PermissionEntity.HOSPITALS_AND_DEPARTMENTS) && (
            <Button
              variant="contained"
              startIcon={<AddTwoToneIcon />}
              onClick={() => setOpenAddModal(true)}
            >
              {t('add_hospital')}
            </Button>
          )}
        </Stack>
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('name')}</TableCell>
                <TableCell>{t('code')}</TableCell>
                <TableCell>{t('phone')}</TableCell>
                <TableCell>{t('email')}</TableCell>
                <TableCell>{t('active')}</TableCell>
                <TableCell align="right">{t('actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {!loadingGet && hospitals.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    {t('no_hospitals')}
                  </TableCell>
                </TableRow>
              )}
              {hospitals.map((hospital) => (
                <TableRow key={hospital.id} hover>
                  <TableCell sx={{ fontWeight: 'bold' }}>
                    {hospital.name}
                  </TableCell>
                  <TableCell>{hospital.code || ''}</TableCell>
                  <TableCell>{hospital.phone || ''}</TableCell>
                  <TableCell>{hospital.email || ''}</TableCell>
                  <TableCell>
                    {hospital.active ? t('yes') : t('no')}
                  </TableCell>
                  <TableCell align="right">
                    {hasEditPermission(
                      PermissionEntity.HOSPITALS_AND_DEPARTMENTS,
                      hospital
                    ) && (
                      <IconButton
                        color="primary"
                        onClick={() => setCurrentHospital(hospital)}
                      >
                        <EditTwoToneIcon fontSize="small" />
                      </IconButton>
                    )}
                    {hasDeletePermission(
                      PermissionEntity.HOSPITALS_AND_DEPARTMENTS,
                      hospital
                    ) && (
                      <IconButton
                        color="error"
                        onClick={() => {
                          setHospitalToDelete(hospital);
                          setOpenDelete(true);
                        }}
                      >
                        <DeleteTwoToneIcon fontSize="small" />
                      </IconButton>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </Box>
      {renderModal()}
      <ConfirmDialog
        open={openDelete}
        onCancel={() => {
          setOpenDelete(false);
          setHospitalToDelete(null);
        }}
        onConfirm={() =>
          dispatch(deleteHospital(hospitalToDelete.id))
            .then(onDeleteSuccess)
            .catch(onDeleteFailure)
        }
        confirmText={t('to_delete')}
        question={t('confirm_delete_hospital')}
      />
    </>
  );
}

export default Hospitals;
