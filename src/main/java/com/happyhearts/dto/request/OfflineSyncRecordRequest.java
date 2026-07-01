package com.happyhearts.dto.request;

import com.happyhearts.enums.ScanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.Instant;

@Data
public class OfflineSyncRecordRequest {

    @NotBlank
    private String rfidCardUid;

    @NotNull
    private Instant scannedAt;

    @NotNull
    private ScanType scanType;
}
