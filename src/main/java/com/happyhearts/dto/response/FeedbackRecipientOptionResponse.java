package com.happyhearts.dto.response;

import com.happyhearts.enums.Role;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class FeedbackRecipientOptionResponse {
    UUID userId;
    String email;
    String displayName;
    Role role;
    String branchCode;
    /** HR category label source (e.g. COOK, SECURITY_GUARD). */
    String employeeCategory;
    /** Human-readable role/category for UI. */
    String roleLabel;
    String profilePhotoUrl;
}
