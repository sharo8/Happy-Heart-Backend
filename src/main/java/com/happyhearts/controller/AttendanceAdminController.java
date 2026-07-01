package com.happyhearts.controller;

import com.happyhearts.dto.request.CreateExcuseRequest;
import com.happyhearts.dto.request.CreateGracePeriodRequest;
import com.happyhearts.dto.request.DecideGracePeriodRequest;
import com.happyhearts.enums.GracePeriodStatus;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.BranchAccessService;
import com.happyhearts.service.BranchAnalyticsService;
import com.happyhearts.service.ExcuseService;
import com.happyhearts.service.GracePeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceAdminController {

    private final GracePeriodService gracePeriodService;
    private final ExcuseService excuseService;
    private final BranchAnalyticsService branchAnalyticsService;
    private final BranchAccessService branchAccessService;

    @GetMapping("/grace-periods")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<?> listGrace(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        GracePeriodStatus st = status != null && !status.isBlank()
                ? GracePeriodStatus.valueOf(status.toUpperCase()) : null;
        return ResponseEntity.ok(Map.of("items", gracePeriodService.list(
                principal,
                parseUuid(employeeId),
                parseUuid(branchId),
                st,
                parseDate(from),
                parseDate(to)
        )));
    }

    @GetMapping("/grace-recipients")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> graceRecipients(@RequestParam String branchId) {
        return ResponseEntity.ok(Map.of("items", gracePeriodService.eligibleRecipients(UUID.fromString(branchId))));
    }

    @PostMapping("/grace-periods")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> createGrace(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateGracePeriodRequest request
    ) {
        return ResponseEntity.ok(gracePeriodService.create(principal, request));
    }

    @PutMapping("/grace-periods/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','LEAD_TEACHER','CENTRAL_COORDINATOR')")
    public ResponseEntity<?> approveGrace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody DecideGracePeriodRequest request
    ) {
        return ResponseEntity.ok(gracePeriodService.approve(principal, id, request.getApproverExplanation()));
    }

    @PutMapping("/grace-periods/{id}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','LEAD_TEACHER','CENTRAL_COORDINATOR')")
    public ResponseEntity<?> rejectGrace(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody DecideGracePeriodRequest request
    ) {
        return ResponseEntity.ok(gracePeriodService.reject(principal, id, request.getApproverExplanation()));
    }

    @GetMapping("/excuses")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<?> listExcuses(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        return ResponseEntity.ok(Map.of("items", excuseService.list(
                principal,
                parseUuid(employeeId),
                parseUuid(branchId),
                parseDate(from),
                parseDate(to)
        )));
    }

    @PostMapping("/excuses")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<?> createExcuse(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody CreateExcuseRequest request
    ) {
        return ResponseEntity.ok(excuseService.create(principal, request));
    }

    @GetMapping("/branch-analytics")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','LEAD_TEACHER','CENTRAL_COORDINATOR')")
    public ResponseEntity<?> branchAnalytics(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String branchId
    ) {
        UUID requested = parseUuid(branchId);
        UUID branchFilter = branchAccessService.resolveBranchFilter(principal, requested);
        if (branchFilter != null) {
            branchAccessService.assertBranchScope(principal, branchFilter);
        }
        return ResponseEntity.ok(branchAnalyticsService.branchAnalytics(
                parseDate(from),
                parseDate(to),
                branchFilter
        ));
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return UUID.fromString(raw);
    }

    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return LocalDate.parse(raw);
    }
}
