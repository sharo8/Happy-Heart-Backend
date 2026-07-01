package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class PostMessageRequest {

    @NotBlank
    @Size(max = 20_000)
    private String content;

    private UUID replyToId;
}
