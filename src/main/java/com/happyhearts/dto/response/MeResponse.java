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
public class MeResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private Role role;
    private Language preferredLanguage;
    private UUID branchId;
    private String branchCode;
    private String branchName;
    private String profilePhotoUrl;
}
