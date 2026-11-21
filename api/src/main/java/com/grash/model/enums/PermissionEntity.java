package com.grash.model.enums;

public enum PermissionEntity {
    PEOPLE_AND_TEAMS,
    CATEGORIES,
    CATEGORIES_WEB,//unused
    WORK_ORDERS,
    PREVENTIVE_MAINTENANCES,
    ASSETS,
    PARTS_AND_MULTIPARTS,
    PURCHASE_ORDERS,
    METERS,
    VENDORS_AND_CUSTOMERS,
    FILES,
    LOCATIONS,
    SETTINGS,
    REQUESTS,
    ANALYTICS,
    // Appended for hospital/department master data. Never reorder existing values:
    // they are persisted as SMALLINT ordinals.
    HOSPITALS_AND_DEPARTMENTS
}
