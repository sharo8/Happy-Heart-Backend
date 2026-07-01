package com.happyhearts.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.happyhearts.enums.CalendarEntryType;
import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarDayEventResponse {
    UUID entryId;
    CalendarEntryType type;
    String label;
    /** LEGACY = calendar_entries ; SCHOOL = calendar_events (PDF import). */
    String entrySource;
    String schoolEventType;
    boolean tentative;
}
