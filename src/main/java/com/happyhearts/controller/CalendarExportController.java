package com.happyhearts.controller;

import com.happyhearts.enums.Role;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.CalendarAccessService;
import com.happyhearts.service.CalendarExcelExportService;
import com.happyhearts.service.CalendarPdfExportService;
import com.lowagie.text.DocumentException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/calendar")
@RequiredArgsConstructor
public class CalendarExportController {

    private final CalendarExcelExportService calendarExcelExportService;
    private final CalendarPdfExportService calendarPdfExportService;
    private final CalendarAccessService calendarAccessService;

    @GetMapping("/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public void exportExcel(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String schoolYear,
            @RequestParam(required = false) UUID branchId,
            HttpServletResponse response
    ) throws IOException {
        UUID effective = branchId != null ? branchId : principal.getBranchId();
        if (effective == null
                && principal.getRole() != Role.SUPER_ADMIN
                && principal.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId is required");
        }
        if (effective != null) {
            calendarAccessService.assertCanView(principal, effective);
        }

        String safeName = schoolYear.replaceAll("[^0-9A-Za-z\\-]", "_");
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"calendar-" + safeName + ".xlsx\"");
        response.setHeader("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);

        calendarExcelExportService.generate(schoolYear, effective, response.getOutputStream());
    }

    @GetMapping("/export/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER','ASSISTANT','COOK','CLEANER','TEACHER')")
    public void exportPdf(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam String schoolYear,
            @RequestParam(required = false) UUID branchId,
            HttpServletResponse response
    ) throws IOException, DocumentException {
        UUID effective = branchId != null ? branchId : principal.getBranchId();
        if (effective == null
                && principal.getRole() != Role.SUPER_ADMIN
                && principal.getRole() != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "branchId is required");
        }
        if (effective != null) {
            calendarAccessService.assertCanView(principal, effective);
        }

        String safeName = schoolYear.replaceAll("[^0-9A-Za-z\\-]", "_");
        response.setContentType("application/pdf");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"calendar-" + safeName + ".pdf\"");
        response.setHeader("Access-Control-Expose-Headers", HttpHeaders.CONTENT_DISPOSITION);

        calendarPdfExportService.generate(schoolYear, effective, response.getOutputStream());
    }
}
