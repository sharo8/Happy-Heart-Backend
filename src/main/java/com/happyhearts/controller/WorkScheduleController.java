package com.happyhearts.controller;



import com.happyhearts.dto.request.EmployeeWorkScheduleRequest;

import com.happyhearts.security.PortalSecurityExpressions;

import com.happyhearts.security.UserPrincipal;

import com.happyhearts.service.WorkScheduleService;

import com.happyhearts.service.Esp32AttendanceService;

import com.happyhearts.service.StaffScopeService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;

import org.springframework.security.access.prepost.PreAuthorize;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PutMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;



import java.util.Map;

import java.util.UUID;



@RestController

@RequestMapping("/api/v1/attendance")

@RequiredArgsConstructor

public class WorkScheduleController {



    private final WorkScheduleService workScheduleService;

    private final Esp32AttendanceService esp32AttendanceService;

    private final StaffScopeService staffScopeService;



    @GetMapping("/work-schedules")

    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<?> listSchedules(

            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(required = false) String branchId

    ) {

        UUID branchUuid = branchId != null && !branchId.isBlank() ? UUID.fromString(branchId) : null;

        return ResponseEntity.ok(Map.of("items", workScheduleService.listSchedules(principal, branchUuid)));

    }



    @PutMapping("/work-schedules/{employeeId}")

    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)

    public ResponseEntity<?> updateSchedule(

            @AuthenticationPrincipal UserPrincipal principal,

            @PathVariable String employeeId,

            @RequestBody EmployeeWorkScheduleRequest request

    ) {

        return ResponseEntity.ok(workScheduleService.updateSchedule(

                principal, UUID.fromString(employeeId), request));

    }



    @GetMapping("/early-departures")

    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<?> earlyDepartures(

            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(required = false) String from,

            @RequestParam(required = false) String to,

            @RequestParam(required = false) String branchId,

            @RequestParam(defaultValue = "1") int page,

            @RequestParam(defaultValue = "20") int limit

    ) {

        UUID scopedBranch = branchId != null && !branchId.isBlank() ? UUID.fromString(branchId) : null;

        scopedBranch = workScheduleService.resolveScopedBranchId(principal, scopedBranch);

        String scopedBranchStr = scopedBranch != null ? scopedBranch.toString() : null;

        return ResponseEntity.ok(esp32AttendanceService.getEarlyDepartures(
                from, to, scopedBranchStr,
                staffScopeService.resolveEmployeeFilter(principal, null),
                page, limit));

    }

}

