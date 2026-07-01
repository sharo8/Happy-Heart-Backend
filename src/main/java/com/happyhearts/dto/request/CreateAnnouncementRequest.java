package com.happyhearts.dto.request;

import com.happyhearts.enums.AnnouncementAudienceType;
import com.happyhearts.enums.AnnouncementPriority;
import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
public class CreateAnnouncementRequest {

    @NotBlank(message = "{validation.announcement.title.required}")
    @Size(max = 100, message = "{validation.announcement.title.max}")
    private String title;

    @NotBlank(message = "{validation.announcement.body.required}")
    @Size(max = 1000, message = "{validation.announcement.body.max}")
    private String body;

    @NotNull(message = "{validation.announcement.priority.required}")
    private AnnouncementPriority priority;

    @NotNull(message = "{validation.announcement.audienceType.required}")
    private AnnouncementAudienceType audienceType;

    private List<Role> roles;
    private List<UUID> branchIds;
    private List<EmployeeCategory> categories;

    private boolean sendImmediately = true;
    private Instant scheduledAt;
    private Instant expiresAt;

    private boolean emailNotification = true;

    @Size(max = 500, message = "{validation.announcement.emailSubject.max}")
    private String emailSubject;

    private boolean saveAsDraft = false;

    @AssertTrue(message = "{error.announcement.audience.invalid}")
    public boolean isAudienceValid() {
        if (saveAsDraft) {
            return true;
        }
        if (audienceType == null) {
            return false;
        }
        return switch (audienceType) {
            case EVERYONE -> true;
            case ROLE -> roles != null && !roles.isEmpty();
            case BRANCH -> branchIds != null && !branchIds.isEmpty();
            case CATEGORY -> categories != null && !categories.isEmpty();
            case CUSTOM -> {
                boolean r = roles != null && !roles.isEmpty();
                boolean b = branchIds != null && !branchIds.isEmpty();
                boolean c = categories != null && !categories.isEmpty();
                yield r || b || c;
            }
        };
    }

    @AssertTrue(message = "{error.announcement.schedule.invalid}")
    public boolean isScheduleValid() {
        if (saveAsDraft) {
            return true;
        }
        if (sendImmediately) {
            return true;
        }
        return scheduledAt != null;
    }
}
