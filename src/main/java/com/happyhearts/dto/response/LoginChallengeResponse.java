package com.happyhearts.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginChallengeResponse {
    private String devOtp;
    private UUID challengeId;
    private String emailMasked;
    private Instant expiresAt;
    private int otpExpiresInMinutes;
}
