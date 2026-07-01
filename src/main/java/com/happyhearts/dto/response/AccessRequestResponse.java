package com.happyhearts.dto.response;

import com.happyhearts.enums.AccessRequestStatus;
import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class AccessRequestResponse {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private String message;
    private Language preferredLanguage;
    private AccessRequestStatus status;
    private String adminMessage;
    private Role assignedRole;
    private UUID assignedBranchId;
    private String assignedBranchCode;
    private String assignedBranchName;
    private String reviewedByName;
    private String reviewedByEmail;
    private Instant reviewedAt;
    private UUID createdUserId;
    private Instant createdAt;
}
