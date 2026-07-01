package com.happyhearts.service;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import com.happyhearts.exception.AccessDeniedException;
import com.happyhearts.model.Employee;
import com.happyhearts.security.UserPrincipal;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Component
public class BranchAccessService {

    private final StaffScopeService staffScopeService;

    public BranchAccessService(StaffScopeService staffScopeService) {
        this.staffScopeService = staffScopeService;
    }

    public static final Set<EmployeeCategory> LEAD_TEACHER_MANAGED_CATEGORIES = EnumSet.of(
            EmployeeCategory.TEACHER_ASSISTANT,
            EmployeeCategory.COOK,
            EmployeeCategory.CLEANER,
            EmployeeCategory.SECURITY_GUARD
    );

    public void assertSuperAdmin(UserPrincipal principal) {
        if (principal.getRole() != Role.SUPER_ADMIN) {
            throw new AccessDeniedException();
        }
    }

    /** Org-wide HR read (all branches): Super Admin and General Manager Pédagogique. */
    public void assertOrgWideHrRead(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return;
        }
        throw new AccessDeniedException();
    }

    /**
     * Org-wide delegated write for GM: {@link com.happyhearts.security.GmpWriteBlockingFilter}
     * enforces per-page permissions before the request reaches services.
     */
    public void assertSuperAdminOrGmDelegatedWrite(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return;
        }
        throw new AccessDeniedException();
    }

    public boolean isLeadTeacherManagedCategory(EmployeeCategory category) {
        return category != null && LEAD_TEACHER_MANAGED_CATEGORIES.contains(category);
    }

    /**
     * Read access to a branch: Super Admin, General Manager Pédagogique (read-only org-wide),
     * Central Coordinator, Lead Teacher, or field staff on their own branch.
     */
    public void assertBranchScope(UserPrincipal principal, UUID branchId) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return;
        }
        if (principal.getRole() == Role.CENTRAL_COORDINATOR
                && principal.getBranchId() != null
                && principal.getBranchId().equals(branchId)) {
            return;
        }
        if (principal.getRole() == Role.LEAD_TEACHER
                && principal.getBranchId() != null
                && principal.getBranchId().equals(branchId)) {
            return;
        }
        if (principal.getRole().isStaffSelfService()) {
            UUID ownBranch = resolveStaffBranchId(principal);
            if (ownBranch != null && ownBranch.equals(branchId)) {
                return;
            }
        }
        throw new AccessDeniedException();
    }

    private UUID resolveStaffBranchId(UserPrincipal principal) {
        if (principal.getBranchId() != null) {
            return principal.getBranchId();
        }
        return staffScopeService.findLinkedEmployee(principal)
                .map(e -> e.getBranch().getId())
                .orElse(null);
    }

    /**
     * Branch HR mutations (suspend employment, schedules): Super Admin, Central Coordinator,
     * or Lead Teacher of the branch.
     */
    public void assertBranchManagementWrite(UserPrincipal principal, UUID branchId) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == Role.CENTRAL_COORDINATOR
                && principal.getBranchId() != null
                && principal.getBranchId().equals(branchId)) {
            return;
        }
        if (principal.getRole() == Role.LEAD_TEACHER
                && principal.getBranchId() != null
                && principal.getBranchId().equals(branchId)) {
            return;
        }
        throw new AccessDeniedException();
    }

    /**
     * Lead Teacher may only mutate staff in their branch (assistants, cooks, cleaners, guards).
     */
    public void assertLeadTeacherCanManageEmployee(UserPrincipal principal, Employee employee) {
        if (principal.getRole() != Role.LEAD_TEACHER) {
            return;
        }
        assertBranchManagementWrite(principal, employee.getBranch().getId());
        if (!isLeadTeacherManagedCategory(employee.getCategory())) {
            throw new AccessDeniedException();
        }
    }

    public void assertLeadTeacherCanAssignCategory(UserPrincipal principal, EmployeeCategory category) {
        if (principal.getRole() != Role.LEAD_TEACHER) {
            return;
        }
        if (!isLeadTeacherManagedCategory(category)) {
            throw new AccessDeniedException();
        }
    }

    public void assertLeadTeacherUsesOwnBranch(UserPrincipal principal, UUID requestedBranchId) {
        if (principal.getRole() != Role.LEAD_TEACHER) {
            return;
        }
        if (principal.getBranchId() == null || !principal.getBranchId().equals(requestedBranchId)) {
            throw new AccessDeniedException();
        }
    }

    public UUID resolveBranchFilter(UserPrincipal principal, UUID requestedBranchId) {
        if (principal.getRole() == Role.SUPER_ADMIN || principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return requestedBranchId;
        }
        if (principal.getBranchId() != null) {
            return principal.getBranchId();
        }
        if (principal.getRole().isStaffSelfService()) {
            return resolveStaffBranchId(principal);
        }
        return principal.getBranchId();
    }

    public void assertAnnouncementAdmin(UserPrincipal principal) {
        if (principal.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (principal.getRole() == Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return;
        }
        if (principal.getRole() == Role.CENTRAL_COORDINATOR || principal.getRole() == Role.LEAD_TEACHER) {
            if (principal.getBranchId() != null) {
                return;
            }
        }
        throw new AccessDeniedException();
    }
}
