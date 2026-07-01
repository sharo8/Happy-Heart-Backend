package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateAnnouncementRequest;
import com.happyhearts.dto.response.AnnouncementResponse;
import com.happyhearts.dto.response.RecipientCountResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<PageData<AnnouncementResponse>>> listAdmin(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "status") String statusFilter,
            @RequestParam(required = false, name = "priority") String priorityFilter,
            @RequestParam(required = false, name = "audience") String audienceFilter,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Boolean myOnly
    ) {
        PageData<AnnouncementResponse> data = announcementService.listAdmin(
                principal, page, size, search, statusFilter, priorityFilter, audienceFilter, from, to, myOnly);
        String msg = messageSource.getMessage("announcement.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/for-me")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<PageData<AnnouncementResponse>>> listForMe(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, name = "filter") String quickFilter
    ) {
        PageData<AnnouncementResponse> data = announcementService.listForMe(principal, page, size, search, quickFilter);
        String msg = messageSource.getMessage("announcement.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/unread-count")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<Long>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long n = announcementService.countUnreadForMe(principal);
        return ResponseEntity.ok(ApiResponse.ok("", n));
    }

    @PostMapping("/preview-recipient-count")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<RecipientCountResponse>> previewRecipients(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAnnouncementRequest request
    ) {
        RecipientCountResponse data = announcementService.previewRecipientCount(principal, request);
        return ResponseEntity.ok(ApiResponse.ok("", data));
    }

    @PostMapping
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<AnnouncementResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateAnnouncementRequest request
    ) {
        AnnouncementResponse data = announcementService.publish(principal, request);
        String msg = messageSource.getMessage("announcement.published.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<AnnouncementResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody CreateAnnouncementRequest request
    ) {
        AnnouncementResponse data = announcementService.update(principal, id, request);
        String msg = messageSource.getMessage("announcement.updated.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        announcementService.delete(principal, id);
        String msg = messageSource.getMessage("announcement.deleted.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<AnnouncementResponse>> resend(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        AnnouncementResponse data = announcementService.resend(principal, id);
        String msg = messageSource.getMessage("announcement.resent.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<Void>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        announcementService.markRead(principal, id);
        return ResponseEntity.ok(ApiResponse.ok("", null));
    }
}
