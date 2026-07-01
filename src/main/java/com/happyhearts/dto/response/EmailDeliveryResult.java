package com.happyhearts.dto.response;

import com.happyhearts.enums.NotificationStatus;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

/** SMTP/SendGrid outcome for staff emails (Messages → Emails tab). */
@Value
@Builder
public class EmailDeliveryResult {
    NotificationStatus status;
    UUID notificationId;
    String provider;
    String recipientEmail;
    String outboundSubject;
    String errorMessage;

    public boolean isSent() {
        return status == NotificationStatus.SENT;
    }
}
