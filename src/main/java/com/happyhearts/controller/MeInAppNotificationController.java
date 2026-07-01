package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.response.InAppNotificationResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.InAppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/in-app-notifications")
@RequiredArgsConstructor
@PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
public class MeInAppNotificationController {

    private final InAppNotificationService inAppNotificationService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ApiResponse<List<InAppNotificationResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<InAppNotificationResponse> data = inAppNotificationService.listForUser(principal, limit);
        String msg = messageSource.getMessage("inapp.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long n = inAppNotificationService.countUnread(principal);
        return ResponseEntity.ok(ApiResponse.ok(null, n));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse<InAppNotificationResponse>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        InAppNotificationResponse data = inAppNotificationService.markRead(principal, id);
        String msg = messageSource.getMessage("inapp.read.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        inAppNotificationService.markAllRead(principal);
        String msg = messageSource.getMessage("inapp.readall.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        inAppNotificationService.deleteReadForUser(principal, id);
        String msg = messageSource.getMessage("inapp.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @DeleteMapping("/read")
    public ResponseEntity<ApiResponse<Integer>> deleteAllRead(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        int deleted = inAppNotificationService.deleteAllReadForUser(principal);
        String msg = messageSource.getMessage("inapp.delete.read.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, deleted));
    }
}
