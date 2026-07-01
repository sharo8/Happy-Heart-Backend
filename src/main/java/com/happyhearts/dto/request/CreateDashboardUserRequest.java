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
public class CreateDashboardUserRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    /** Optional; shown in audit columns instead of raw email when set. */
    @Size(max = 100, message = "{validation.name.max}")
    private String firstName;

    @Size(max = 100, message = "{validation.name.max}")
    private String lastName;

    @NotNull(message = "{validation.role.required}")
    private Role role;

    @NotNull(message = "{validation.language.required}")
    private Language preferredLanguage;

    /** Required for branch-scoped dashboard roles; must be null for global roles. */
    private UUID branchId;

    @AssertTrue(message = "{validation.user.branch.manager}")
    public boolean isBranchSetWhenBranchScoped() {
        if (role != Role.CENTRAL_COORDINATOR && role != Role.LEAD_TEACHER) {
            return true;
        }
        return branchId != null;
    }

    @AssertTrue(message = "{validation.user.branch.superadmin}")
    public boolean isBranchAbsentWhenGlobal() {
        if (role != Role.SUPER_ADMIN && role != Role.GENERAL_MANAGER_PEDAGOGIQUE) {
            return true;
        }
        return branchId == null;
    }
}
