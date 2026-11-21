import { Audit } from './audit';
import Hospital, { HospitalMiniDTO } from './hospital';

export default interface Department extends Audit {
  id: number;
  name: string;
  code?: string;
  description?: string;
  hospital: Hospital | HospitalMiniDTO;
  parentDepartment?: DepartmentMiniDTO | null;
  active: boolean;
}

export interface DepartmentMiniDTO {
  id: number;
  name: string;
  code?: string;
  hospitalId?: number;
}
