package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.response.BranchManagerLookupResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.DashboardUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Queries under {@code /api/v1/users} that are allowed for Branch Manager + Super Admin (not admin-only CRUD).
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class BranchManagerLookupController {

    private final DashboardUserService dashboardUserService;

    @GetMapping("/branch-manager")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<BranchManagerLookupResponse>> branchManagerByBranch(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID branchId
    ) {
        BranchManagerLookupResponse data = dashboardUserService.findBranchManagerForBranch(principal, branchId);
        return ResponseEntity.ok(ApiResponse.ok(null, data));
    }
}
