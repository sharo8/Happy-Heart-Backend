package com.happyhearts.service;

import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CalendarAccessService {

    /**
     * Calendar is branch-scoped and readable by:
     * - Super Admin (all branches)
     * - General Manager Pédagogique (all branches, read-only)
     * - Central Coordinator / Lead Teacher (same branch)
     * - Field staff linked to an Employee record (same branch)
     */
    public void assertCanView(UserPrincipal principal, UUID branchId) {
        if (principal.getRole() == Role.SUPER_ADMIN) return;
        if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) return;

        if (principal.getBranchId() != null && principal.getBranchId().equals(branchId)) return;

        // Either wrong branch or missing principal branch id.
        throw new AccessDeniedException();
    }

    /**
     * Calendar mutations: Super Admin only.
     */
    public void assertCanEdit(UserPrincipal principal) {
        if (principal.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException();
        }
    }
}

