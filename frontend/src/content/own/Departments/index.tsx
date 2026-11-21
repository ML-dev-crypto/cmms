import { Helmet } from 'react-helmet-async';
import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
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
  addDepartment,
  deleteDepartment,
  editDepartment,
  getDepartments
} from '../../../slices/department';
import { getHospitals } from '../../../slices/hospital';
import Form from '../components/form';
import ConfirmDialog from '../components/ConfirmDialog';
import PermissionErrorMessage from '../components/PermissionErrorMessage';
import { IField } from '../type';
import Department from '../../../models/owns/department';
import { CustomSnackBarContext } from '../../../contexts/CustomSnackBarContext';
import { TitleContext } from '../../../contexts/TitleContext';
import useAuth from '../../../hooks/useAuth';
import { PermissionEntity } from '../../../models/owns/role';

function Departments() {
  const { t }: { t: any } = useTranslation();
  const { setTitle } = useContext(TitleContext);
  const { showSnackBar } = useContext(CustomSnackBarContext);
  const dispatch = useDispatch();
  const { departments, loadingGet } = useSelector((state) => state.departments);
  const {
    hasViewPermission,
    hasCreatePermission,
    hasEditPermission,
    hasDeletePermission
  } = useAuth();
  const [openAddModal, setOpenAddModal] = useState(false);
  const [currentDepartment, setCurrentDepartment] =
    useState<Department | null>(null);
  const [departmentToDelete, setDepartmentToDelete] =
    useState<Department | null>(null);
  const [openDelete, setOpenDelete] = useState(false);

  useEffect(() => {
    setTitle(t('departments'));
    if (hasViewPermission(PermissionEntity.HOSPITALS_AND_DEPARTMENTS)) {
      dispatch(getDepartments());
      dispatch(getHospitals());
    }
  }, []);

  const fields: Array<IField> = [
    {
      name: 'name',
      type: 'text',
      label: t('name'),
      placeholder: t('department_name'),
      required: true
    },
    {
      name: 'hospital',
      type: 'select',
      type2: 'hospital',
      label: t('hospital'),
      placeholder: t('select_hospital'),
      required: true
    },
    {
      name: 'code',
      type: 'text',
      label: t('code'),
      placeholder: t('department_code'),
      midWidth: true
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
    name: Yup.string().required(t('required_department_name')),
    hospital: Yup.object().required(t('required_hospital'))
  };

  const onSaveSuccess = () => {
    setOpenAddModal(false);
    setCurrentDepartment(null);
    showSnackBar(t('changes_saved_success'), 'success');
  };
  const onSaveFailure = () => showSnackBar(t('department_save_failure'), 'error');
  const onDeleteSuccess = () => {
    setOpenDelete(false);
    setDepartmentToDelete(null);
    showSnackBar(t('department_remove_success'), 'success');
  };
  const onDeleteFailure = () =>
    showSnackBar(t('department_remove_failure'), 'error');

  const getDepartmentFormValues = (department: Department) => ({
    ...department,
    hospital: department?.hospital
      ? {
          label: department.hospital.name,
          value: department.hospital.id
        }
      : null,
    active: department.active ?? true
  });

  const renderModal = () => (
    <Dialog
      fullWidth
      maxWidth="md"
      open={openAddModal || !!currentDepartment}
      onClose={() => {
        setOpenAddModal(false);
        setCurrentDepartment(null);
      }}
    >
      <DialogTitle sx={{ p: 3 }}>
        <Typography variant="h4" gutterBottom>
          {currentDepartment ? t('edit_department') : t('add_department')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers sx={{ p: 3 }}>
        <Form
          fields={fields}
          validation={Yup.object().shape(shape)}
          submitText={currentDepartment ? t('save') : t('create')}
          values={
            currentDepartment
              ? getDepartmentFormValues(currentDepartment)
              : { active: true }
          }
          onSubmit={async (values) => {
            const formattedValues: Partial<Department> = {
              ...values,
              hospital: values.hospital?.value
                ? ({ id: values.hospital.value } as Department['hospital'])
                : (null as unknown as Department['hospital'])
            };
            try {
              if (currentDepartment) {
                await dispatch(
                  editDepartment(currentDepartment.id, formattedValues)
                );
              } else {
                await dispatch(addDepartment(formattedValues));
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
        <title>{t('departments')}</title>
      </Helmet>
      <Box paddingX={4} paddingY={2}>
        <Stack
          direction="row"
          justifyContent="space-between"
          alignItems="center"
          mb={2}
        >
          <Typography variant="h3">{t('departments')}</Typography>
          {hasCreatePermission(PermissionEntity.HOSPITALS_AND_DEPARTMENTS) && (
            <Button
              variant="contained"
              startIcon={<AddTwoToneIcon />}
              onClick={() => setOpenAddModal(true)}
            >
              {t('add_department')}
            </Button>
          )}
        </Stack>
        <TableContainer component={Paper}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('name')}</TableCell>
                <TableCell>{t('code')}</TableCell>
                <TableCell>{t('hospital')}</TableCell>
                <TableCell>{t('active')}</TableCell>
                <TableCell align="right">{t('actions')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {!loadingGet && departments.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} align="center">
                    {t('no_departments')}
                  </TableCell>
                </TableRow>
              )}
              {departments.map((department) => (
                <TableRow key={department.id} hover>
                  <TableCell sx={{ fontWeight: 'bold' }}>
                    {department.name}
                  </TableCell>
                  <TableCell>{department.code || ''}</TableCell>
                  <TableCell>{department.hospital?.name || ''}</TableCell>
                  <TableCell>
                    {department.active ? t('yes') : t('no')}
                  </TableCell>
                  <TableCell align="right">
                    {hasEditPermission(
                      PermissionEntity.HOSPITALS_AND_DEPARTMENTS,
                      department
                    ) && (
                      <IconButton
                        color="primary"
                        onClick={() => setCurrentDepartment(department)}
                      >
                        <EditTwoToneIcon fontSize="small" />
                      </IconButton>
                    )}
                    {hasDeletePermission(
                      PermissionEntity.HOSPITALS_AND_DEPARTMENTS,
                      department
                    ) && (
                      <IconButton
                        color="error"
                        onClick={() => {
                          setDepartmentToDelete(department);
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
          setDepartmentToDelete(null);
        }}
        onConfirm={() =>
          dispatch(deleteDepartment(departmentToDelete.id))
            .then(onDeleteSuccess)
            .catch(onDeleteFailure)
        }
        confirmText={t('to_delete')}
        question={t('confirm_delete_department')}
      />
    </>
  );
}

export default Departments;
