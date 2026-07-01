package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ToggleMessageReactionRequest {
    @NotBlank
    private String emoji;
}
