package com.happyhearts.dto.response;

import com.happyhearts.enums.FeedbackType;
import com.happyhearts.enums.FeedbackVisibility;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class FeedbackResponse {
    UUID id;
    UUID fromUserId;
    String fromEmail;
    String fromDisplayName;
    UUID toUserId;
    String toEmail;
    String toDisplayName;
    FeedbackType type;
    String content;
    FeedbackVisibility visibility;
    boolean sentByEmail;
    Instant emailSentAt;
    Instant createdAt;
    Instant updatedAt;
    UUID updatedByUserId;
    String updatedByDisplayName;
    UUID branchId;
    String branchCode;
}
