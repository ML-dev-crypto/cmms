import { formatAssetValues } from '../formatters';

describe('formatAssetValues', () => {
  it('keeps the new hospital metadata in sync with the backend contract', () => {
    const values = formatAssetValues({
      name: 'MRI',
      department: { label: 'Radiology', value: 7 },
      criticality: { label: 'High', value: 'HIGH' },
      equipmentType: { label: 'Imaging', value: 'IMAGING' },
      clinicalImpact: { label: 'Direct', value: 'DIRECT_PATIENT_CARE' }
    });

    expect(values.department).toEqual({ id: 7 });
    expect(values.criticality).toBe('HIGH');
    expect(values.equipmentType).toBe('IMAGING');
    expect(values.clinicalImpact).toBe('DIRECT_PATIENT_CARE');
  });

  it('accepts raw enum values coming from copied entities', () => {
    const values = formatAssetValues({
      criticality: 'LOW',
      equipmentType: 'OTHER',
      clinicalImpact: 'NON_CLINICAL'
    });

    expect(values.criticality).toBe('LOW');
    expect(values.equipmentType).toBe('OTHER');
    expect(values.clinicalImpact).toBe('NON_CLINICAL');
  });

  it('clears an empty department selection', () => {
    const values = formatAssetValues({ department: null });

    expect(values.department).toBeNull();
  });
});
