package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.AiQueryRequest;
import com.happyhearts.dto.request.ExplanationEmailRequest;
import com.happyhearts.model.ExplanationRequest;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.AiService;
import com.happyhearts.service.ExplanationRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttendanceFeaturesController {

    private final ExplanationRequestService explanationRequestService;
    private final AiService aiService;
    private final MessageSource messageSource;

    @PostMapping("/notifications/email")
    @PreAuthorize(PortalSecurityExpressions.MANAGEMENT_ROLES)
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendExplanationEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ExplanationEmailRequest request
    ) {
        ExplanationRequest saved = explanationRequestService.sendExplanationEmail(
                principal,
                request.getEmployeeId(),
                request.getTo(),
                request.getSubject(),
                request.getBody()
        );
        String msg = messageSource.getMessage("notification.explanation.sent", null, LocaleContextHolder.getLocale());
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        if (saved.getId() != null) {
            payload.put("id", saved.getId().toString());
        }
        payload.put("status", saved.getStatus().name());
        return ResponseEntity.ok(ApiResponse.ok(msg, payload));
    }

    @PostMapping("/ai/query")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','GENERAL_MANAGER_PEDAGOGIQUE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> aiQuery(
            @Valid @RequestBody AiQueryRequest request
    ) {
        Map<String, Object> result = aiService.query(request.getQuestion(), request.getContext());
        String msg = messageSource.getMessage("ai.query.success", null, "OK", LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, result));
    }
}
