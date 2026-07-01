package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateConversationRequest {

    @Size(max = 255)
    private String subject;

    @NotEmpty
    private List<UUID> recipientUserIds;

    @NotBlank
    @Size(max = 20_000)
    private String firstMessage;
}
