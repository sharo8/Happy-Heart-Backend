package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AssignRfidRequest {

    @NotBlank
    private String rfidCardUid;
}
