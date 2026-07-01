package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Esp32OfflineRecordRequest {

    @NotBlank
    private String uid;

    @NotBlank
    private String timestamp;
}
