package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SendInternalEmailRequest {
    @NotEmpty
    private List<UUID> toUserIds;
    private List<UUID> ccUserIds;
    @NotBlank
    private String subject;
    @NotBlank
    private String body;
    private String label;
    private UUID draftId;
    private UUID replyToId;
}
