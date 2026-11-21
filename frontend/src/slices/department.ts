import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import Department, { DepartmentMiniDTO } from '../models/owns/department';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

interface DepartmentState {
  departments: Department[];
  departmentsMini: DepartmentMiniDTO[];
  loadingGet: boolean;
}

const initialState: DepartmentState = {
  departments: [],
  departmentsMini: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'departments',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getDepartments(
      state: DepartmentState,
      action: PayloadAction<{ departments: Department[] }>
    ) {
      state.departments = action.payload.departments;
    },
    getDepartmentsMini(
      state: DepartmentState,
      action: PayloadAction<{ departments: DepartmentMiniDTO[] }>
    ) {
      state.departmentsMini = action.payload.departments;
    },
    addDepartment(
      state: DepartmentState,
      action: PayloadAction<{ department: Department }>
    ) {
      state.departments = [...state.departments, action.payload.department];
    },
    editDepartment(
      state: DepartmentState,
      action: PayloadAction<{ department: Department }>
    ) {
      state.departments = state.departments.map((department) =>
        department.id === action.payload.department.id
          ? action.payload.department
          : department
      );
    },
    deleteDepartment(
      state: DepartmentState,
      action: PayloadAction<{ id: number }>
    ) {
      state.departments = state.departments.filter(
        (department) => department.id !== action.payload.id
      );
    },
    setLoadingGet(
      state: DepartmentState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getDepartments =
  (hospitalId?: number): AppThunk =>
  async (dispatch) => {
    dispatch(slice.actions.setLoadingGet({ loading: true }));
    const departments = await api.get<Department[]>(
      hospitalId ? `departments?hospitalId=${hospitalId}` : 'departments'
    );
    dispatch(slice.actions.getDepartments({ departments }));
    dispatch(slice.actions.setLoadingGet({ loading: false }));
  };

export const getDepartmentsMini =
  (hospitalId?: number): AppThunk =>
  async (dispatch) => {
    const departments = await api.get<DepartmentMiniDTO[]>(
      hospitalId ? `departments/mini?hospitalId=${hospitalId}` : 'departments/mini'
    );
    dispatch(slice.actions.getDepartmentsMini({ departments }));
  };

export const addDepartment =
  (department: Partial<Department>): AppThunk =>
  async (dispatch) => {
    const created = await api.post<Department>('departments', department);
    dispatch(slice.actions.addDepartment({ department: created }));
    return created;
  };

export const editDepartment =
  (id: number, department: Partial<Department>): AppThunk =>
  async (dispatch) => {
    const updated = await api.patch<Department>(
      `departments/${id}`,
      department
    );
    dispatch(slice.actions.editDepartment({ department: updated }));
    return updated;
  };

export const deleteDepartment =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(
      `departments/${id}`
    );
    if (response.success) {
      dispatch(slice.actions.deleteDepartment({ id }));
    }
  };

export default slice;
