package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.response.EmailNotificationResponse;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final MessageSource messageSource;

    @PostMapping("/daily-report")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<Void>> triggerDailyReport(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam UUID branchId,
            @RequestParam LocalDate date
    ) {
        notificationService.triggerDailyReport(principal, branchId, date);
        String msg = messageSource.getMessage("notification.daily.triggered", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageData<EmailNotificationResponse>>> logs(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationType type
    ) {
        PageData<EmailNotificationResponse> data =
                notificationService.logs(principal, page, size, status, type);
        String msg = messageSource.getMessage("notification.logs.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }
}
