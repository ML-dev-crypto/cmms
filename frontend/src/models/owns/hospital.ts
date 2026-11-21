import { Audit } from './audit';

export default interface Hospital extends Audit {
  id: number;
  name: string;
  code?: string;
  address?: string;
  phone?: string;
  email?: string;
  timezone?: string;
  description?: string;
  active: boolean;
}

export interface HospitalMiniDTO {
  id: number;
  name: string;
  code?: string;
}
