package com.happyhearts.enums;

/**
 * Portal roles. Staff linked to an {@link com.happyhearts.model.Employee} record get branch-scoped roles
 * (CENTRAL_COORDINATOR, LEAD_TEACHER, ASSISTANT, COOK, CLEANER, TEACHER) via HR sync.
 */
public enum Role {
    SUPER_ADMIN,
    GENERAL_MANAGER_PEDAGOGIQUE,
    CENTRAL_COORDINATOR,
    LEAD_TEACHER,
    ASSISTANT,
    COOK,
    CLEANER,
    TEACHER;

    public boolean isStaffSelfService() {
        return this == ASSISTANT || this == COOK || this == CLEANER || this == TEACHER;
    }

    public boolean isBranchScopedDashboard() {
        return this == CENTRAL_COORDINATOR || this == LEAD_TEACHER;
    }

    /** Portal roles that must be tied to a branch (dashboard or HR staff). */
    public boolean requiresBranchAssignment() {
        return isBranchScopedDashboard() || isStaffSelfService();
    }

    /** Roles Super Admin can assign when creating a dashboard user (not staff synced from HR). */
    public boolean isAssignableFromAdminUserForm() {
        return this == SUPER_ADMIN
                || this == GENERAL_MANAGER_PEDAGOGIQUE
                || this == CENTRAL_COORDINATOR
                || this == LEAD_TEACHER;
    }

    public boolean isReadOnlyGlobalPedagogy() {
        return this == GENERAL_MANAGER_PEDAGOGIQUE;
    }
}
