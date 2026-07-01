package com.happyhearts.dto.response;

import com.happyhearts.enums.ReaderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RfidReaderResponse {

    private UUID id;
    private UUID branchId;
    private String readerCode;
    private ReaderType readerType;
    private String locationDescription;
    private boolean online;
    private Instant lastSyncAt;
    private int pendingSyncCount;
    private Instant createdAt;
}
