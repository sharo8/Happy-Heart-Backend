package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.response.BranchDirectoryEntryResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.StaffPortalService;
import com.happyhearts.service.StaffScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/portal")
@RequiredArgsConstructor
@PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
public class StaffPortalController {

    private final StaffPortalService staffPortalService;
    private final StaffScopeService staffScopeService;

    @GetMapping("/branch-directory")
    public ResponseEntity<ApiResponse<List<BranchDirectoryEntryResponse>>> branchDirectory(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("ok", staffPortalService.branchDirectory(principal)));
    }

    @GetMapping("/my-employee-id")
    public ResponseEntity<ApiResponse<Map<String, String>>> myEmployeeId(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(ApiResponse.ok("ok", Map.of(
                "employeeId", staffScopeService.requireOwnEmployeeId(principal).toString()
        )));
    }
}
