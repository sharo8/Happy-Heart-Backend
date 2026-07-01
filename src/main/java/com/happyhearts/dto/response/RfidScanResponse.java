package com.happyhearts.dto.response;

import com.happyhearts.enums.ScanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfidScanResponse {

    private boolean success;
    private String employeeName;
    private ScanType scanType;
    private Instant scannedAt;
}
