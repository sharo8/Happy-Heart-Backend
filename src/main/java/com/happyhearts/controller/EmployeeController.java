package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.AssignRfidRequest;
import com.happyhearts.dto.request.CreateEmployeeRequest;
import com.happyhearts.dto.request.UpdateEmployeeRequest;
import com.happyhearts.dto.request.UpdateEmploymentActiveRequest;
import com.happyhearts.dto.response.EmployeeResponse;
import com.happyhearts.dto.response.RfidStatusResponse;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<PageData<EmployeeResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) EmployeeCategory category,
            @RequestParam(required = false) Boolean managerOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageData<EmployeeResponse> data = employeeService.search(principal, branchId, category, managerOnly, page, size);
        String msg = messageSource.getMessage("employee.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<EmployeeResponse>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        EmployeeResponse data = employeeService.getById(principal, id);
        String msg = messageSource.getMessage("employee.get.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<EmployeeResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateEmployeeRequest request
    ) {
        EmployeeResponse data = employeeService.create(principal, request);
        String msg = messageSource.getMessage("employee.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PatchMapping("/{id}/employment-active")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<EmployeeResponse>> setEmploymentActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmploymentActiveRequest request
    ) {
        EmployeeResponse data = employeeService.setEmploymentActive(principal, id, request);
        String msg = messageSource.getMessage("employee.employment.updated", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<EmployeeResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmployeeRequest request
    ) {
        EmployeeResponse data = employeeService.update(principal, id, request);
        String msg = messageSource.getMessage("employee.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        employeeService.delete(principal, id);
        String msg = messageSource.getMessage("employee.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/{id}/rfid/assign")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<RfidStatusResponse>> assignRfid(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignRfidRequest request
    ) {
        RfidStatusResponse data = employeeService.assignRfid(principal, id, request);
        String msg = messageSource.getMessage("employee.rfid.assign.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/{id}/rfid/revoke")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<RfidStatusResponse>> revokeRfid(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        RfidStatusResponse data = employeeService.revokeRfid(principal, id);
        String msg = messageSource.getMessage("employee.rfid.revoke.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/{id}/rfid/reactivate")
    @PreAuthorize(PortalSecurityExpressions.BRANCH_STAFF_WRITE)
    public ResponseEntity<ApiResponse<RfidStatusResponse>> reactivateRfid(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        RfidStatusResponse data = employeeService.reactivateRfid(principal, id);
        String msg = messageSource.getMessage("employee.rfid.reactivate.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}/rfid/status")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<RfidStatusResponse>> rfidStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        RfidStatusResponse data = employeeService.rfidStatus(principal, id);
        String msg = messageSource.getMessage("employee.rfid.status.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }
}
