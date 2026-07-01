package com.happyhearts.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Esp32HeartbeatRequest {

    @NotBlank
    private String deviceId;

    @Min(0)
    private int pendingSyncCount;
}
