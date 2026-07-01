package com.happyhearts.security;

/**
 * Shared SpEL fragments for {@link org.springframework.security.access.prepost.PreAuthorize}.
 */
public final class PortalSecurityExpressions {

    /** Read branch-scoped HR / attendance (excludes staff self-service-only flows). */
    public static final String BRANCH_HR_READ =
            "hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')";

    /** Any authenticated portal user including field staff. */
    public static final String ANY_PORTAL_USER =
            "hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER',"
                    + "'ASSISTANT','COOK','CLEANER','TEACHER')";

    /** Super Admin, GMP, Central Coordinator, Lead Teacher (e.g. notifications admin list). */
    public static final String MANAGEMENT_ROLES =
            "hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')";

    /** Branch-scoped staff HR write (Super Admin, GM delegated, Central Coordinator, Lead Teacher). */
    public static final String BRANCH_STAFF_WRITE =
            "hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')";

    private PortalSecurityExpressions() {
    }
}
