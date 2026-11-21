package com.grash.model.enums;

/**
 * Clinical impact of an equipment failure. Used for repair prioritisation and
 * reporting. Persisted as a string and appended only.
 */
public enum ClinicalImpact {
    DIRECT_PATIENT_CARE,
    INDIRECT_PATIENT_CARE,
    NON_CLINICAL
}
