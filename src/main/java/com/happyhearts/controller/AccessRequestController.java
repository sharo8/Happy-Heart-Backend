package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.ApproveAccessRequestRequest;
import com.happyhearts.dto.request.RejectAccessRequestRequest;
import com.happyhearts.dto.request.SubmitAccessRequestRequest;
import com.happyhearts.dto.response.AccessRequestResponse;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.AccessRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AccessRequestController {

    private final AccessRequestService accessRequestService;

    @PostMapping("/api/v1/public/access-requests")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> submit(
            @Valid @RequestBody SubmitAccessRequestRequest request
    ) {
        AccessRequestResponse data = accessRequestService.submit(request);
        return ResponseEntity.ok(ApiResponse.ok("access.request.submitted", data));
    }

    @GetMapping("/api/v1/access-requests")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<AccessRequestResponse>>> list(
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(ApiResponse.ok("ok", accessRequestService.listAll(status)));
    }

    @PostMapping("/api/v1/access-requests/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> approve(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ApproveAccessRequestRequest request
    ) {
        AccessRequestResponse data = accessRequestService.approve(principal, id, request);
        return ResponseEntity.ok(ApiResponse.ok("access.request.approved", data));
    }

    @PostMapping("/api/v1/access-requests/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AccessRequestResponse>> reject(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RejectAccessRequestRequest request
    ) {
        AccessRequestResponse data = accessRequestService.reject(principal, id, request);
        return ResponseEntity.ok(ApiResponse.ok("access.request.rejected", data));
    }
}
