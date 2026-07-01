package com.happyhearts.dto.request;

import com.happyhearts.enums.ReaderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RfidReaderRequest {

    @NotNull
    private UUID branchId;

    @NotBlank
    private String readerCode;

    @NotNull
    private ReaderType readerType;

    private String locationDescription;
}
