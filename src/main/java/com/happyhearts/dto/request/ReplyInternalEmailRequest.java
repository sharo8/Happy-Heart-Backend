package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReplyInternalEmailRequest {
    @NotBlank
    private String body;
    private boolean replyAll;
}
