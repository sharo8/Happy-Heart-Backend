package com.happyhearts.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.happyhearts.enums.CalendarEntryType;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarDayResponse {
    LocalDate date;
    CalendarEntryType dominantType;
    String dominantLabel;
    List<CalendarDayEventResponse> events;

    /** Shown on the first day of a multi-day {@code calendar_events} span only. */
    String eventTitle;
    String eventType;
    String eventColorBg;
    String eventColorText;
    Boolean isTentative;
}
