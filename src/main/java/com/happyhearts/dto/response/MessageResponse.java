package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Value
@Builder
public class MessageResponse {
    UUID id;
    UUID conversationId;
    UUID senderId;
    String senderEmail;
    String senderDisplayName;
    String senderProfilePhotoUrl;
    String content;
    Instant sentAt;
    boolean emailSent;
    Instant emailSentAt;
    boolean read;
    UUID replyToId;
    String replyToPreview;
    Instant editedAt;
    boolean deleted;
    List<MessageReactionResponse> reactions;
}
