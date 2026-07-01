package com.happyhearts.dto.request;

import com.happyhearts.enums.Language;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SubmitAccessRequestRequest {

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 50)
    private String phone;

    @NotBlank(message = "{validation.access.request.message.required}")
    @Size(max = 2000)
    private String message;

    private Language preferredLanguage;
}
