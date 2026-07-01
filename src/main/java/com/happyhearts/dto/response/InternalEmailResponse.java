package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class InternalEmailResponse {
    UUID id;
    UUID fromUserId;
    String fromDisplayName;
    String fromEmail;
    UUID toUserId;
    String toDisplayName;
    String toEmail;
    String deliveredToEmail;
    List<UUID> ccUserIds;
    String subject;
    String body;
    String bodyPreview;
    String folder;
    boolean read;
    boolean starred;
    String label;
    UUID threadId;
    UUID replyToId;
    Instant sentAt;
    Instant createdAt;
    int threadCount;
    String smtpStatus;
    String smtpProvider;
    String outboundSubject;
    UUID emailNotificationId;
    String smtpErrorMessage;
}
