package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
public class DeviceTokenResponse {
    String token;
    Instant expiresAt;
    String deviceId;
}
