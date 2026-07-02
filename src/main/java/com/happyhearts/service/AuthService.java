package com.happyhearts.service;

import com.happyhearts.config.AuthOtpProperties;
import com.happyhearts.dto.request.FirstPasswordSetupRequest;
import com.happyhearts.dto.request.ForgotPasswordRequest;
import com.happyhearts.dto.request.LoginRequest;
import com.happyhearts.dto.request.RefreshTokenRequest;
import com.happyhearts.dto.request.SetPasswordRequest;
import com.happyhearts.dto.request.VerifyOtpRequest;
import com.happyhearts.dto.response.AuthResponse;
import com.happyhearts.dto.response.LoginChallengeResponse;
import com.happyhearts.dto.response.TokenResponse;
import com.happyhearts.exception.BusinessException;
import com.happyhearts.model.LoginOtpChallenge;
import com.happyhearts.model.PasswordResetToken;
import com.happyhearts.model.User;
import com.happyhearts.repository.LoginOtpChallengeRepository;
import com.happyhearts.repository.PasswordResetTokenRepository;
import com.happyhearts.repository.UserRepository;
import com.happyhearts.security.JwtTokenProvider;
import com.happyhearts.security.UserPrincipal;
import com.happyhearts.util.OtpUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.security.SecureRandom;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final LoginOtpChallengeRepository loginOtpChallengeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthOtpProperties authOtpProperties;
    private final EmailService emailService;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthService.class);



    @Transactional
    public LoginChallengeResponse initiateLogin(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByEmailIgnoreCase(principal.getUsername()).orElseThrow();
        assertAccountReadyForPasswordLogin(user);

        loginOtpChallengeRepository.deleteUnusedByUserId(user.getId());

        String otp = OtpUtils.generateNumericOtp(authOtpProperties.getOtpLength());
        Instant expiresAt = Instant.now().plus(authOtpProperties.getOtpExpirationMinutes(), ChronoUnit.MINUTES);

        LoginOtpChallenge challenge = LoginOtpChallenge.builder()
                .user(user)
                .codeHash(passwordEncoder.encode(otp))
                .expiresAt(expiresAt)
                .used(false)
                .failedAttempts(0)
                .build();
        challenge = loginOtpChallengeRepository.save(challenge);

        boolean emailSent = true;
        try {
            emailService.sendLoginOtp(user, otp);
        } catch (Exception e) {
            emailSent = false;
            log.warn("Failed to send OTP email to {}, returning OTP in response instead. Reason: {}", user.getEmail(), e.getMessage());
        }

        return LoginChallengeResponse.builder()
                .challengeId(challenge.getId())
                .emailMasked(OtpUtils.maskEmail(user.getEmail()))
                .expiresAt(expiresAt)
                .otpExpiresInMinutes(authOtpProperties.getOtpExpirationMinutes())
                .devOtp(emailSent ? null : otp)
                .build();
    }







    @Transactional
    public AuthResponse verifyOtpAndIssueTokens(VerifyOtpRequest request) {
        LoginOtpChallenge challenge = loginOtpChallengeRepository.findWithUserById(request.getChallengeId())
                .orElseThrow(() -> new BusinessException("error.otp.invalid"));
        User user = challenge.getUser();
        if (!user.getEmail().equalsIgnoreCase(request.getEmail())) {
            throw new BusinessException("error.otp.invalid");
        }
        if (challenge.isUsed()) {
            throw new BusinessException("error.otp.used");
        }
        if (challenge.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("error.otp.expired");
        }
        if (challenge.getFailedAttempts() >= authOtpProperties.getOtpMaxAttempts()) {
            throw new BusinessException("error.otp.locked");
        }
        if (!passwordEncoder.matches(request.getOtp(), challenge.getCodeHash())) {
            challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
            loginOtpChallengeRepository.save(challenge);
            throw new BusinessException("error.otp.invalid");
        }
        challenge.setUsed(true);
        loginOtpChallengeRepository.save(challenge);

        User fresh = userRepository.findByEmailIgnoreCase(user.getEmail()).orElseThrow();
        UserPrincipal up = new UserPrincipal(fresh);
        String access = jwtTokenProvider.createAccessToken(up, fresh.isPasswordChangeRequired());
        String refresh = jwtTokenProvider.createRefreshToken(up);
        return AuthResponse.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .role(fresh.getRole())
                .language(fresh.getPreferredLanguage())
                .passwordChangeRequired(fresh.isPasswordChangeRequired())
                .branchId(fresh.getBranch() != null ? fresh.getBranch().getId() : null)
                .build();
    }

    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshTokenRequest request) {
        String token = request.getRefreshToken();
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isRefreshToken(token)) {
            throw new BusinessException("error.auth.invalid");
        }
        String email = jwtTokenProvider.getSubject(token);
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BusinessException("error.auth.invalid"));
        UserPrincipal up = new UserPrincipal(user);
        return new TokenResponse(jwtTokenProvider.createAccessToken(up, user.isPasswordChangeRequired()));
    }

    @Transactional
    public void completeFirstPasswordSetup(FirstPasswordSetupRequest request) {
        User user = userRepository.findByInitialSetupToken(request.getToken())
                .orElseThrow(() -> new BusinessException("error.setup.token.invalid"));
        if (user.getInitialSetupTokenExpiresAt() == null
                || user.getInitialSetupTokenExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("error.setup.token.expired");
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setInitialSetupToken(null);
        user.setInitialSetupTokenExpiresAt(null);
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.getEmail().trim().toLowerCase())
                .ifPresent(user -> {
                    passwordResetTokenRepository.deleteAllUnusedForUser(user.getId());
                    String token = generateSecureToken();
                    Instant expiresAt = Instant.now().plus(authOtpProperties.getPasswordResetTokenExpirationHours(), ChronoUnit.HOURS);
                    PasswordResetToken resetToken = PasswordResetToken.builder()
                            .user(user)
                            .token(token)
                            .expiresAt(expiresAt)
                            .build();
                    passwordResetTokenRepository.save(resetToken);
                    emailService.sendPasswordResetLink(user, token);
                });
    }

    @Transactional
    public void resetPassword(SetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findValidByToken(request.getToken(), Instant.now())
                .orElseThrow(() -> new BusinessException("error.password.reset.token.invalid"));
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setInitialSetupToken(null);
        user.setInitialSetupTokenExpiresAt(null);
        user.setPasswordChangeRequired(false);
        userRepository.save(user);
        resetToken.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(resetToken);
    }

    private void assertAccountReadyForPasswordLogin(User user) {
        if (user.getInitialSetupToken() != null
                && user.getInitialSetupTokenExpiresAt() != null
                && user.getInitialSetupTokenExpiresAt().isAfter(Instant.now())) {
            throw new BusinessException("error.auth.complete.setup.first");
        }
        if (user.getInitialSetupToken() != null) {
            throw new BusinessException("error.setup.token.expired");
        }
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
