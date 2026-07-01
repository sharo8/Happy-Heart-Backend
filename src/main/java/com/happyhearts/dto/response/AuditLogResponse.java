package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class AuditLogResponse {
    UUID id;
    UUID userId;
    String userEmail;
    String userDisplayName;
    String action;
    UUID targetId;
    String targetType;
    String targetLabel;
    String details;
    String ipAddress;
    String userAgent;
    Instant createdAt;
    UUID branchId;
    String branchCode;
    String branchName;
}
