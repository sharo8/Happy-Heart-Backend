package com.happyhearts.dto.request;

import lombok.Data;

import java.time.LocalTime;

@Data
public class EmployeeWorkScheduleRequest {

    private Boolean useBranchSchedule;

    private LocalTime workStartTime;

    private LocalTime workEndTime;

    private Integer gracePeriodMinutes;

    /** Required when grace period minutes change on a custom work schedule. */
    private String graceExplanation;
}
