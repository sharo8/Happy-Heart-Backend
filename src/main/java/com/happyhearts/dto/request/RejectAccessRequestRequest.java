package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectAccessRequestRequest {

    @NotBlank(message = "{validation.access.request.rejection.required}")
    @Size(max = 2000)
    private String adminMessage;
}
