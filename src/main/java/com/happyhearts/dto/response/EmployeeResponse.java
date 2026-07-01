package com.happyhearts.dto.response;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Language;
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
public class EmployeeResponse {

    private UUID id;
    /** Linked portal user (for messaging / feedback recipient pickers). */
    private UUID portalUserId;
    private UUID branchId;
    private String branchCode;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private EmployeeCategory category;
    private Language preferredLanguage;
    private String profilePhotoUrl;
    private String rfidCardUid;
    private boolean rfidActive;
    private boolean employmentActive;
    private String createdByEmail;
    private String updatedByEmail;
    private String createdByName;
    private String updatedByName;
    private Instant createdAt;
    private Instant updatedAt;
}
