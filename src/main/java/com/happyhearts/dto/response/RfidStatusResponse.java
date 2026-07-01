package com.happyhearts.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfidStatusResponse {

    private String rfidCardUid;
    private boolean active;
    private Instant assignedAt;
}
