package com.happyhearts.controller;

import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.BranchAccessService;
import com.happyhearts.service.Esp32AttendanceService;
import com.happyhearts.service.StaffScopeService;
import com.happyhearts.service.SseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class RfidEsp32AttendanceController {

    private final Esp32AttendanceService esp32AttendanceService;
    private final SseService sseService;
    private final BranchAccessService branchAccessService;
    private final StaffScopeService staffScopeService;

    @Data
    public static class ScanRequest {
        @NotBlank
        private String uid;
        @NotBlank
        private String deviceId;
    }

    @Data
    public static class OfflineSyncRequest {
        private List<OfflineRecord> records;

        @Data
        public static class OfflineRecord {
            @NotBlank
            private String uid;
            @NotBlank
            private String timestamp;
            @NotBlank
            private String deviceId;
        }
    }

    @Data
    public static class UnknownScanRequest {
        @NotBlank
        private String uid;
        @NotBlank
        private String deviceId;
    }

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@Valid @RequestBody ScanRequest req) {
        try {
            Map<String, Object> result = esp32AttendanceService.processScan(
                    req.getUid().toUpperCase().trim(),
                    req.getDeviceId()
            );
            if (result.get("error") != null
                    && (result.get("error").equals("checkout_cooldown") || result.get("error").equals("checkin_cooldown"))) {
                sseService.broadcast("scan_notice", result);
                return ResponseEntity.status(409).body(result);
            }
            if (!Boolean.TRUE.equals(result.get("duplicate"))) {
                sseService.broadcast("scan", result);
                sseService.broadcast("stats_update", esp32AttendanceService.getLiveStats());
            } else {
                Map<String, Object> notice = new java.util.LinkedHashMap<>(result);
                if (!notice.containsKey("message") || notice.get("message") == null) {
                    notice.put("message", "This scan was ignored because the same action was recorded moments ago.");
                }
                notice.put("error", "duplicate_scan");
                sseService.broadcast("scan_notice", notice);
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("unknown_card")) {
                String uid = req.getUid().toUpperCase().trim();
                return handleUnknownCardScan(uid, req.getDeviceId());
            }
            if (msg != null && msg.contains("inactive_employee")) {
                Map<String, Object> body = Map.of(
                        "error", "inactive_employee",
                        "message", "Employee account is inactive"
                );
                sseService.broadcast("scan_notice", body);
                return ResponseEntity.status(403).body(body);
            }
            return ResponseEntity.status(500).body(Map.of("error", "server_error", "message", msg));
        } catch (com.happyhearts.exception.BusinessException e) {
            return ResponseEntity.status(503).body(Map.of(
                    "error", "reader_not_configured",
                    "message", "Register RFID reader ESP32_DOOR_01 in Spring (restart backend to auto-seed)"
            ));
        }
    }

    @PostMapping("/sync-offline")
    public ResponseEntity<?> syncOffline(@Valid @RequestBody OfflineSyncRequest req) {
        List<Map<String, Object>> records = req.getRecords() == null ? List.of() : req.getRecords().stream()
                .map(r -> Map.<String, Object>of(
                        "uid", r.getUid(),
                        "timestamp", r.getTimestamp(),
                        "deviceId", r.getDeviceId()
                ))
                .toList();
        Map<String, Object> syncResult = esp32AttendanceService.syncOffline(records);
        if (syncResult.get("synced") instanceof Number n && n.intValue() > 0) {
            sseService.broadcast("stats_update", esp32AttendanceService.getLiveStats());
        }
        return ResponseEntity.ok(syncResult);
    }

    @PostMapping("/unknown-scan")
    public ResponseEntity<?> unknownScan(@Valid @RequestBody UnknownScanRequest req) {
        String uid = req.getUid().toUpperCase().trim();
        return handleUnknownCardScan(uid, req.getDeviceId());
    }

    private ResponseEntity<?> handleUnknownCardScan(String uid, String deviceId) {
        Map<String, Object> result = esp32AttendanceService.recordUnknownCard(uid, deviceId);
        if (Boolean.TRUE.equals(result.get("cooldown"))) {
            sseService.broadcast("scan_notice", result);
            return ResponseEntity.status(409).body(result);
        }
        sseService.broadcast("unknown_card", Map.of(
                "uid", uid,
                "deviceId", deviceId,
                "lastSeen", result.getOrDefault("lastSeen", "")
        ));
        return ResponseEntity.status(404).body(Map.of(
                "error", "unknown_card",
                "message", "Card not registered to any employee — assign it in RFID Employees",
                "uid", uid,
                "scanCount", result.get("scanCount")
        ));
    }

    @GetMapping("/unknown-cards")
    public ResponseEntity<?> getUnknownCards() {
        return ResponseEntity.ok(Map.of("cards", esp32AttendanceService.getUnknownCards()));
    }

    @PostMapping("/assign-unknown/{uid}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'HR_MANAGER', 'BRANCH_MANAGER')")
    public ResponseEntity<?> assignUnknown(
            @PathVariable String uid,
            @RequestBody Map<String, String> body
    ) {
        String employeeId = body.get("employeeId");
        if (employeeId == null || employeeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "employeeId is required"));
        }
        esp32AttendanceService.assignUnknownCard(uid.toUpperCase(), employeeId);
        sseService.broadcast("card_assigned", Map.of("uid", uid.toUpperCase()));
        return ResponseEntity.ok(Map.of("assigned", true));
    }

    @GetMapping("/recent")
    public ResponseEntity<?> recent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "15") int limit
    ) {
        UUID branchFilter = resolveBranchFilter(principal, null);
        return ResponseEntity.ok(Map.of("items", esp32AttendanceService.getRecentActivity(limit, branchFilter)));
    }

    @GetMapping("/today")
    public ResponseEntity<?> today(@AuthenticationPrincipal UserPrincipal principal) {
        UUID branchFilter = resolveBranchFilter(principal, null);
        return ResponseEntity.ok(esp32AttendanceService.getTodaySummary(branchFilter));
    }

    @GetMapping("/today/latest")
    public ResponseEntity<?> todayLatest(@AuthenticationPrincipal UserPrincipal principal) {
        UUID branchFilter = resolveBranchFilter(principal, null);
        return ResponseEntity.ok(Map.of("items", esp32AttendanceService.getTodayLatestScans(branchFilter)));
    }

    @GetMapping("/employee/{employeeId}/detail")
    public ResponseEntity<?> employeeDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String employeeId,
            @RequestParam(required = false) String date
    ) {
        try {
            UUID empId = java.util.UUID.fromString(employeeId);
            staffScopeService.assertOwnEmployee(principal, empId);
            java.time.LocalDate viewDate = date != null && !date.isBlank()
                    ? java.time.LocalDate.parse(date)
                    : java.time.LocalDate.now(com.happyhearts.util.TimeUtils.kigali());
            return ResponseEntity.ok(esp32AttendanceService.getEmployeeDetail(empId, viewDate));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/employee/{employeeId}/summary")
    public ResponseEntity<?> employeeSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String employeeId,
            @RequestParam(defaultValue = "week") String period
    ) {
        try {
            UUID empId = java.util.UUID.fromString(employeeId);
            staffScopeService.assertOwnEmployee(principal, empId);
            return ResponseEntity.ok(esp32AttendanceService.getEmployeePeriodSummary(empId, period));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/reports/analytics")
    public ResponseEntity<?> analyticsReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String branchId,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "overview") String groupBy,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false, defaultValue = "date") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir
    ) {
        UUID requestedBranch = parseUuid(branchId);
        UUID branchFilter = resolveBranchFilter(principal, requestedBranch);
        String effectiveBranchId = branchFilter != null ? branchFilter.toString() : null;
        UUID scopedEmployee = staffScopeService.resolveEmployeeFilter(principal, parseUuid(employeeId));
        String effectiveEmployeeId = scopedEmployee != null ? scopedEmployee.toString() : employeeId;
        return ResponseEntity.ok(esp32AttendanceService.getAnalyticsReport(
                from, to, effectiveEmployeeId, department, effectiveBranchId, status, groupBy, page, limit, sortBy, sortDir));
    }

    @GetMapping("/report")
    public ResponseEntity<?> report(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit
    ) {
        UUID scopedEmployee = staffScopeService.resolveEmployeeFilter(principal, parseUuid(employeeId));
        String effectiveEmployeeId = scopedEmployee != null ? scopedEmployee.toString() : employeeId;
        return ResponseEntity.ok(esp32AttendanceService.getReport(from, to, effectiveEmployeeId, department, page, limit));
    }

    @GetMapping(value = "/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream() {
        return sseService.subscribe();
    }

    private UUID resolveBranchFilter(UserPrincipal principal, UUID requestedBranchId) {
        if (principal == null) {
            return requestedBranchId;
        }
        return branchAccessService.resolveBranchFilter(principal, requestedBranchId);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return UUID.fromString(raw);
    }
}
