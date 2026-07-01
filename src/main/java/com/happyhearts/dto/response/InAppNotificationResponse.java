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
public class InAppNotificationResponse {
    private UUID id;
    private String title;
    private String body;
    private boolean read;
    private String kind;
    private Instant createdAt;
    private Instant readAt;
}
