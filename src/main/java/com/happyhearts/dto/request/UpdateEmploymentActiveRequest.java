package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateEmploymentActiveRequest {

    @NotNull
    private Boolean active;
}
