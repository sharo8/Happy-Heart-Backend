package com.happyhearts.dto.response;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private Role role;
    private Language language;
    /** True if the user must change password after admin onboarding (JWT also carries claim {@code mch}). */
    private boolean passwordChangeRequired;
    /** Present for branch managers so the client can scope attendance and reports without decoding the JWT. */
    private UUID branchId;
}
