package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.UpdateProfileRequest;
import com.happyhearts.dto.response.MeResponse;
import com.happyhearts.security.PortalSecurityExpressions;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@PreAuthorize(PortalSecurityExpressions.ANY_PORTAL_USER)
public class MeController {

    private final UserProfileService userProfileService;
    private final MessageSource messageSource;

    @GetMapping
    public ResponseEntity<ApiResponse<MeResponse>> get(@AuthenticationPrincipal UserPrincipal principal) {
        MeResponse data = userProfileService.getMe(principal);
        String msg = messageSource.getMessage("user.me.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PatchMapping
    public ResponseEntity<ApiResponse<MeResponse>> patch(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        MeResponse data = userProfileService.updateProfile(principal, request);
        String msg = messageSource.getMessage("user.profile.update.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }
}
