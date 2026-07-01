package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AiQueryRequest {

    @NotBlank
    private String question;

    private String context = "attendance";
}
