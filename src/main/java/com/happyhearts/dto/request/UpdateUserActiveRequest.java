package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateUserActiveRequest {

    @NotNull
    private Boolean active;
}
