package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class ConversationSummaryResponse {
    UUID id;
    String subject;
    Instant createdAt;
    UUID branchId;
    String branchCode;
    List<UUID> participantUserIds;
    List<String> participantNames;
    boolean group;
    String lastMessagePreview;
    Instant lastMessageAt;
    int unreadCount;
    String avatarUrl;
}
