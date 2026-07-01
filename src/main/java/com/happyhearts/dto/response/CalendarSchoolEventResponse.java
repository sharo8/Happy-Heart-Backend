package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

@Value
@Builder
public class CalendarSchoolEventResponse {
    UUID id;
    String title;
    LocalDate startDate;
    LocalDate endDate;
    String eventType;
    boolean tentative;
    String schoolYear;
    boolean applyToAllBranches;
    String source;
    String color;
    UUID periodId;
}
