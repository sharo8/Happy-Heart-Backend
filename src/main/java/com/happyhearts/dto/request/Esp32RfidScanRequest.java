package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Esp32RfidScanRequest {

    @NotBlank
    private String uid;

    @NotBlank
    private String deviceId;

    /** ISO-8601 local or UTC timestamp from device; defaults to server time when omitted. */
    private String timestamp;
}
