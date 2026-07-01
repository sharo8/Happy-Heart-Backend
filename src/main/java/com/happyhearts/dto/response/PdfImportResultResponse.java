package com.happyhearts.dto.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.UUID;

@Value
@Builder
public class PdfImportResultResponse {
    UUID importId;
    String schoolYear;
    int eventsCreated;
    int periodsCreated;
    List<CalendarSchoolEventResponse> events;
    List<String> periodNames;
    String message;
}
