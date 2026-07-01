package com.happyhearts.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private UUID id;
    private String code;
    private String name;
    private String location;
    private Double latitude;
    private Double longitude;
    private LocalTime workStartTime;
    private LocalTime workEndTime;
    private int gracePeriodMinutes;
    private UUID leadTeacherId;
    private String leadTeacherName;
    private UUID secondTeacherId;
    private String secondTeacherName;
    private String createdByEmail;
    private String updatedByEmail;
    /** Resolved from users / employees for UI; may be null if unknown. */
    private String createdByName;
    private String updatedByName;
    private Instant createdAt;
    private Instant updatedAt;
}
