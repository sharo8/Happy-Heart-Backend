package com.happyhearts.dto.response;

import com.happyhearts.enums.AnnouncementAudienceType;
import com.happyhearts.enums.AnnouncementPriority;
import com.happyhearts.enums.AnnouncementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {

    private UUID id;
    private String title;
    private String body;
    private boolean sendToAll;
    private AnnouncementAudienceType audienceType;
    private String targetRoles;
    private List<UUID> targetBranchIds;
    private List<String> targetCategories;
    private AnnouncementPriority priority;
    private String createdByEmail;
    private UUID createdByUserId;
    private Instant createdAt;
    private Instant scheduledAt;
    private Instant expiresAt;
    private boolean sendImmediately;
    private boolean emailNotification;
    private String emailSubject;
    private AnnouncementStatus status;
    /** SENT | SCHEDULED | DRAFT | EXPIRED */
    private String displayStatus;
    private boolean emailSent;
    private int recipientCount;
    /** Present on recipient (for-me) responses */
    private Boolean read;
}
