package com.happyhearts.dto.request;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class ApproveAccessRequestRequest {

    @NotNull(message = "{validation.role.required}")
    private Role role;

    private UUID branchId;

    @NotNull(message = "{validation.language.required}")
    private Language preferredLanguage;

    @Size(max = 2000)
    private String adminMessage;
}
