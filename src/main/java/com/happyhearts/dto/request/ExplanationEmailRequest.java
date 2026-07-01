package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ExplanationEmailRequest {

    @NotBlank
    private String to;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    @NotNull
    private UUID employeeId;
}
