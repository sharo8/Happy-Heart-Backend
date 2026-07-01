package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.OfflineSyncRequest;
import com.happyhearts.dto.request.RfidScanRequest;
import com.happyhearts.dto.response.AttendanceDailyRowResponse;
import com.happyhearts.dto.response.AttendanceSummaryResponse;
import com.happyhearts.dto.response.OfflineSyncResponse;
import com.happyhearts.dto.response.RfidScanResponse;
import com.happyhearts.enums.ExportFormat;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.AttendanceService;
import com.happyhearts.service.RfidScanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
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
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final RfidScanService rfidScanService;
    private final AttendanceService attendanceService;
    private final MessageSource messageSource;

    @PostMapping("/reader/scan")
    @PreAuthorize("hasAuthority('RFID_READER')")
    public ResponseEntity<ApiResponse<RfidScanResponse>> readerScan(@Valid @RequestBody RfidScanRequest request) {
        RfidScanResponse data = rfidScanService.scan(request);
        String msg = messageSource.getMessage("message.scan.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/reader/sync")
    @PreAuthorize("hasAuthority('RFID_READER')")
    public ResponseEntity<ApiResponse<OfflineSyncResponse>> readerSync(@Valid @RequestBody OfflineSyncRequest request) {
        OfflineSyncResponse data = rfidScanService.sync(request);
        String msg = messageSource.getMessage("message.sync.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/daily")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_HR_READ)
    public ResponseEntity<ApiResponse<List<AttendanceDailyRowResponse>>> daily(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID branchId,
            @RequestParam LocalDate date
    ) {
        List<AttendanceDailyRowResponse> data = attendanceService.daily(principal, branchId, date);
        String msg = messageSource.getMessage("attendance.daily.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_HR_READ)
    public ResponseEntity<ApiResponse<List<AttendanceDailyRowResponse>>> employeeHistory(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID employeeId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        List<AttendanceDailyRowResponse> data =
                attendanceService.employeeHistory(principal, employeeId, startDate, endDate);
        String msg = messageSource.getMessage("attendance.employee.history.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/branch/{branchId}/summary")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_HR_READ)
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> branchSummary(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID branchId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate
    ) {
        AttendanceSummaryResponse data =
                attendanceService.branchSummary(principal, branchId, startDate, endDate);
        String msg = messageSource.getMessage("attendance.branch.summary.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/branch/{branchId}/absences")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_HR_READ)
    public ResponseEntity<ApiResponse<List<AttendanceDailyRowResponse>>> absences(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID branchId,
            @RequestParam LocalDate date
    ) {
        List<AttendanceDailyRowResponse> data = attendanceService.absences(principal, branchId, date);
        String msg = messageSource.getMessage("attendance.absences.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/export")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_HR_READ)
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID branchId,
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate,
            @RequestParam ExportFormat format
    ) {
        byte[] bytes = attendanceService.export(principal, branchId, startDate, endDate, format);
        String ext = format == ExportFormat.EXCEL ? "xlsx" : "pdf";
        String contentType = format == ExportFormat.EXCEL
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : MediaType.APPLICATION_PDF_VALUE;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-" + branchId + "." + ext)
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }
}
