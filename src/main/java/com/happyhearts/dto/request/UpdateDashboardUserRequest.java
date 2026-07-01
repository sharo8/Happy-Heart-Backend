package com.happyhearts.dto.request;

import com.happyhearts.enums.Language;
import com.happyhearts.enums.Role;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateDashboardUserRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @Size(max = 100, message = "{validation.name.max}")
    private String firstName;

    @Size(max = 100, message = "{validation.name.max}")
    private String lastName;

    /** Stored on linked HR employee record(s), not on the portal user. */
    private String phone;

    private String profilePhotoUrl;

    @NotNull(message = "{validation.language.required}")
    private Language preferredLanguage;

    @NotNull(message = "{validation.role.required}")
    private Role role;

    /** Required for branch-scoped roles; null for global roles. */
    private UUID branchId;

    @AssertTrue(message = "{validation.user.branch.manager}")
    public boolean isBranchSetWhenBranchScoped() {
        if (role == null || !role.requiresBranchAssignment()) {
            return true;
        }
        return branchId != null;
    }

    @AssertTrue(message = "{validation.user.branch.nonmanager}")
    public boolean isBranchAbsentWhenGlobal() {
        if (role == null || role.requiresBranchAssignment()) {
            return true;
        }
        return branchId == null;
    }
}
