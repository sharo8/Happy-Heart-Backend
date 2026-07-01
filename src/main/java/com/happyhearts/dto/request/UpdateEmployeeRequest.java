package com.happyhearts.dto.request;

import com.happyhearts.enums.EmployeeCategory;
import com.happyhearts.enums.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class UpdateEmployeeRequest {

    @NotNull
    private UUID branchId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @Email
    private String email;

    private String phone;

    @NotNull
    private EmployeeCategory category;

    @NotNull
    private Language preferredLanguage;

    private String profilePhotoUrl;
}
