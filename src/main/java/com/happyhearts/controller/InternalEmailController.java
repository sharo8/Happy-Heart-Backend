package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.ReplyInternalEmailRequest;
import com.happyhearts.dto.request.SaveEmailDraftRequest;
import com.happyhearts.dto.request.SendInternalEmailRequest;
import com.happyhearts.dto.response.InternalEmailResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.InternalEmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/emails")
@RequiredArgsConstructor
public class InternalEmailController {

    private final InternalEmailService internalEmailService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<PageData<InternalEmailResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "inbox") String folder,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageData<InternalEmailResponse> data = internalEmailService.list(principal, folder, q, page, size);
        return ResponseEntity.ok(ApiResponse.ok(msg("email.list.success"), data));
    }

    @GetMapping("/unread-count")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal UserPrincipal principal) {
        long count = internalEmailService.unreadInboxCount(principal);
        return ResponseEntity.ok(ApiResponse.ok(msg("email.unread.success"), Map.of("inbox", count)));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.get.success"), internalEmailService.get(principal, id)));
    }

    @GetMapping("/{id}/thread")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<List<InternalEmailResponse>>> thread(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.thread.success"), internalEmailService.thread(principal, id)));
    }

    @PostMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> send(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody SendInternalEmailRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.send.success"), internalEmailService.send(principal, request)));
    }

    @PostMapping("/drafts")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> saveDraft(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody SaveEmailDraftRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.draft.success"), internalEmailService.saveDraft(principal, request)));
    }

    @PostMapping("/{id}/reply")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> reply(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody ReplyInternalEmailRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.reply.success"), internalEmailService.reply(principal, id, request)));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> markRead(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean read = Boolean.TRUE.equals(body.get("read"));
        return ResponseEntity.ok(ApiResponse.ok(msg("email.read.success"), internalEmailService.markRead(principal, id, read)));
    }

    @PutMapping("/{id}/star")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> star(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body
    ) {
        boolean starred = Boolean.TRUE.equals(body.get("starred"));
        return ResponseEntity.ok(ApiResponse.ok(msg("email.star.success"), internalEmailService.toggleStar(principal, id, starred)));
    }

    @PutMapping("/{id}/label")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> label(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.label.success"), internalEmailService.setLabel(principal, id, body.get("label"))));
    }

    @PutMapping("/{id}/folder")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<InternalEmailResponse>> moveFolder(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body
    ) {
        return ResponseEntity.ok(ApiResponse.ok(msg("email.folder.success"), internalEmailService.moveToFolder(principal, id, body.get("folder"))));
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, key, LocaleContextHolder.getLocale());
    }
}
