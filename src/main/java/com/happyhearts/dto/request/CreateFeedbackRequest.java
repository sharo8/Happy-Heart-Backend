package com.happyhearts.dto.request;

import com.happyhearts.enums.FeedbackType;
import com.happyhearts.enums.FeedbackVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateFeedbackRequest {

    @NotNull
    private UUID toUserId;

    @NotNull
    private FeedbackType type;

    @NotBlank
    @Size(max = 20_000)
    private String content;

    @NotNull
    private FeedbackVisibility visibility;

    /** When false, feedback is stored but no email is sent (optional). */
    private Boolean sentByEmail;
}
