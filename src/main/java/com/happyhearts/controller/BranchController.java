package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.BranchRequest;
import com.happyhearts.dto.response.BranchResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.BranchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<List<BranchResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<BranchResponse> data = branchService.findAccessible(principal);
        String msg = messageSource.getMessage("branch.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<BranchResponse>> get(@PathVariable UUID id) {
        BranchResponse data = branchService.findById(id);
        String msg = messageSource.getMessage("branch.get.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody BranchRequest request
    ) {
        BranchResponse data = branchService.create(principal, request);
        String msg = messageSource.getMessage("branch.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<BranchResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody BranchRequest request
    ) {
        BranchResponse data = branchService.update(principal, id, request);
        String msg = messageSource.getMessage("branch.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        branchService.delete(id);
        String msg = messageSource.getMessage("branch.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}
