package com.happyhearts.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class OfflineSyncRequest {

    @NotNull
    private UUID rfidReaderId;

    @NotEmpty
    @Valid
    private List<OfflineSyncRecordRequest> records;
}
