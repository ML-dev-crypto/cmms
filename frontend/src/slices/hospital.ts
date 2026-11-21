import { createSlice } from '@reduxjs/toolkit';
import type { PayloadAction } from '@reduxjs/toolkit';
import type { AppThunk } from 'src/store';
import Hospital, { HospitalMiniDTO } from '../models/owns/hospital';
import api from '../utils/api';
import { revertAll } from 'src/utils/redux';

interface HospitalState {
  hospitals: Hospital[];
  hospitalsMini: HospitalMiniDTO[];
  loadingGet: boolean;
}

const initialState: HospitalState = {
  hospitals: [],
  hospitalsMini: [],
  loadingGet: false
};

const slice = createSlice({
  name: 'hospitals',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    getHospitals(
      state: HospitalState,
      action: PayloadAction<{ hospitals: Hospital[] }>
    ) {
      state.hospitals = action.payload.hospitals;
    },
    getHospitalsMini(
      state: HospitalState,
      action: PayloadAction<{ hospitals: HospitalMiniDTO[] }>
    ) {
      state.hospitalsMini = action.payload.hospitals;
    },
    addHospital(
      state: HospitalState,
      action: PayloadAction<{ hospital: Hospital }>
    ) {
      state.hospitals = [...state.hospitals, action.payload.hospital];
    },
    editHospital(
      state: HospitalState,
      action: PayloadAction<{ hospital: Hospital }>
    ) {
      state.hospitals = state.hospitals.map((hospital) =>
        hospital.id === action.payload.hospital.id
          ? action.payload.hospital
          : hospital
      );
    },
    deleteHospital(state: HospitalState, action: PayloadAction<{ id: number }>) {
      state.hospitals = state.hospitals.filter(
        (hospital) => hospital.id !== action.payload.id
      );
    },
    setLoadingGet(
      state: HospitalState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      state.loadingGet = action.payload.loading;
    }
  }
});

export const reducer = slice.reducer;

export const getHospitals = (): AppThunk => async (dispatch) => {
  dispatch(slice.actions.setLoadingGet({ loading: true }));
  const hospitals = await api.get<Hospital[]>('hospitals');
  dispatch(slice.actions.getHospitals({ hospitals }));
  dispatch(slice.actions.setLoadingGet({ loading: false }));
};

export const getHospitalsMini = (): AppThunk => async (dispatch) => {
  const hospitals = await api.get<HospitalMiniDTO[]>('hospitals/mini');
  dispatch(slice.actions.getHospitalsMini({ hospitals }));
};

export const addHospital =
  (hospital: Partial<Hospital>): AppThunk =>
  async (dispatch) => {
    const created = await api.post<Hospital>('hospitals', hospital);
    dispatch(slice.actions.addHospital({ hospital: created }));
    return created;
  };

export const editHospital =
  (id: number, hospital: Partial<Hospital>): AppThunk =>
  async (dispatch) => {
    const updated = await api.patch<Hospital>(`hospitals/${id}`, hospital);
    dispatch(slice.actions.editHospital({ hospital: updated }));
    return updated;
  };

export const deleteHospital =
  (id: number): AppThunk =>
  async (dispatch) => {
    const response = await api.deletes<{ success: boolean }>(`hospitals/${id}`);
    if (response.success) {
      dispatch(slice.actions.deleteHospital({ id }));
    }
  };

export default slice;
