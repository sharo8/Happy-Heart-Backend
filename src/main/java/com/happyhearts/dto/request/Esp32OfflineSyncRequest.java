package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Esp32OfflineSyncRequest {

    @NotBlank
    private String uid;

    @NotBlank
    private String timestamp;

    @NotBlank
    private String deviceId;
}
