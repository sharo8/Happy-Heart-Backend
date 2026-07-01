package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.CreateDashboardUserRequest;
import com.happyhearts.dto.request.UpdateDashboardUserRequest;
import com.happyhearts.dto.request.UpdateUserActiveRequest;
import com.happyhearts.dto.response.CreatedUserResponse;
import com.happyhearts.dto.response.UserListResponse;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.DashboardUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final DashboardUserService dashboardUserService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<List<UserListResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<UserListResponse> data = dashboardUserService.listDashboardUsers(principal);
        return ResponseEntity.ok(ApiResponse.ok(null, data));
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<UserListResponse>> setActive(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserActiveRequest request
    ) {
        UserListResponse data = dashboardUserService.setUserActive(principal, id, request);
        String msg = messageSource.getMessage("user.active.updated", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<CreatedUserResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateDashboardUserRequest request
    ) {
        CreatedUserResponse data = dashboardUserService.createDashboardUser(principal, request);
        String msg = messageSource.getMessage("user.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<UserListResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDashboardUserRequest request
    ) {
        UserListResponse data = dashboardUserService.updateDashboardUser(principal, id, request);
        String msg = messageSource.getMessage("user.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        dashboardUserService.deleteDashboardUser(principal, id);
        String msg = messageSource.getMessage("user.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}
