package com.happyhearts.dto.response;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.NotificationStatus;
import com.happyhearts.enums.NotificationType;
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
public class EmailNotificationResponse {

    private UUID id;
    private String recipientEmail;
    private String subject;
    private Language language;
    private NotificationType notificationType;
    private NotificationStatus status;
    private Instant sentAt;
    private Instant createdAt;
}
