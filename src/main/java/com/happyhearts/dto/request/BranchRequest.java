package com.happyhearts.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalTime;
import java.util.UUID;

@Data
public class BranchRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String name;

    private String location;

    private Double latitude;

    private Double longitude;

    @NotNull
    private LocalTime workStartTime;

    private LocalTime workEndTime;

    @NotNull
    private Integer gracePeriodMinutes;

    private UUID leadTeacherId;

    private UUID secondTeacherId;
}
