package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.PageData;
import com.happyhearts.dto.request.CreateFeedbackRequest;
import com.happyhearts.dto.request.UpdateFeedbackRequest;
import com.happyhearts.dto.response.FeedbackRecipientOptionResponse;
import com.happyhearts.dto.response.FeedbackResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.FeedbackService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;
    private final MessageSource messageSource;

    @GetMapping
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<PageData<FeedbackResponse>>> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir
    ) {
        PageData<FeedbackResponse> data = feedbackService.list(principal, page, Math.min(size, 100), q, sort, dir);
        String msg = messageSource.getMessage("feedback.list.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/recipient-suggestions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<List<FeedbackRecipientOptionResponse>>> recipientSuggestions(
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        List<FeedbackRecipientOptionResponse> data = feedbackService.recipientSuggestions(principal);
        String msg = messageSource.getMessage("feedback.recipients.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
    public ResponseEntity<ApiResponse<FeedbackResponse>> get(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        FeedbackResponse data = feedbackService.getById(principal, id);
        String msg = messageSource.getMessage("feedback.get.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateFeedbackRequest request
    ) {
        FeedbackResponse data = feedbackService.create(principal, request);
        String msg = messageSource.getMessage("feedback.create.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<FeedbackResponse>> update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFeedbackRequest request
    ) {
        FeedbackResponse data = feedbackService.update(principal, id, request);
        String msg = messageSource.getMessage("feedback.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE','CENTRAL_COORDINATOR','LEAD_TEACHER')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id
    ) {
        feedbackService.delete(principal, id);
        String msg = messageSource.getMessage("feedback.delete.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}
