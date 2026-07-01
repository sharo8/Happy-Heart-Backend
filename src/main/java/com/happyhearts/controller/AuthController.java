package com.happyhearts.controller;

import com.happyhearts.dto.ApiResponse;
import com.happyhearts.dto.request.FirstPasswordSetupRequest;
import com.happyhearts.dto.request.ForgotPasswordRequest;
import com.happyhearts.dto.request.LoginRequest;
import com.happyhearts.dto.request.RefreshTokenRequest;
import com.happyhearts.dto.request.SetPasswordRequest;
import com.happyhearts.dto.request.VerifyOtpRequest;
import com.happyhearts.dto.response.AuthResponse;
import com.happyhearts.dto.response.LoginChallengeResponse;
import com.happyhearts.dto.response.TokenResponse;
import com.happyhearts.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.i18n.LocaleContextHolder;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MessageSource messageSource;

    /**
     * Step 1: validate email + password; sends OTP to the user's email (never returns JWT).
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginChallengeResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginChallengeResponse data = authService.initiateLogin(request);
        String msg = messageSource.getMessage("auth.login.otp.sent", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    /**
     * Step 2: validate OTP and return access + refresh tokens.
     */
    @PostMapping("/verify-otp")
    public ResponseEntity<ApiResponse<AuthResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        AuthResponse data = authService.verifyOtpAndIssueTokens(request);
        String msg = messageSource.getMessage("auth.verify.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    /**
     * First sign-in: set password using the token from the onboarding email (public).
     */
    @PostMapping("/first-login/setup-password")
    public ResponseEntity<ApiResponse<Void>> setupFirstPassword(@Valid @RequestBody FirstPasswordSetupRequest request) {
        authService.completeFirstPasswordSetup(request);
        String msg = messageSource.getMessage("auth.setup.password.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        String msg = messageSource.getMessage("auth.password.reset.requested", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody SetPasswordRequest request) {
        authService.resetPassword(request);
        String msg = messageSource.getMessage("auth.password.reset.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse data = authService.refresh(request);
        String msg = messageSource.getMessage("auth.refresh.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        String msg = messageSource.getMessage("message.logout.success", null, LocaleContextHolder.getLocale());
        return ResponseEntity.ok(ApiResponse.ok(msg, null));
    }
}
