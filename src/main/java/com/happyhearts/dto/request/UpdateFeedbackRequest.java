package com.happyhearts.dto.request;

import com.happyhearts.enums.FeedbackType;
import com.happyhearts.enums.FeedbackVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateFeedbackRequest {

    private FeedbackType type;

    private FeedbackVisibility visibility;

    @NotBlank
    @Size(max = 10000)
    private String content;

  /** Required when Super Admin edits an evaluation they did not create. */
    @Size(max = 500)
    private String editReason;
}
