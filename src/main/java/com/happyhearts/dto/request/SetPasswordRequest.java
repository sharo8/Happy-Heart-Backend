package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SetPasswordRequest {

    @NotBlank
    @Size(min = 8, max = 128)
    private String token;

    @NotBlank
    @Size(min = 10, max = 128)
    private String newPassword;
}
