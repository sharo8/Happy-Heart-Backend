package com.happyhearts.service;

import com.happyhearts.enums.CalendarEntryType;
import com.happyhearts.enums.EventType;
import org.springframework.stereotype.Component;

/**
 * Maps school {@link EventType} to legacy {@link CalendarEntryType} slices used by the calendar grid API.
 */
@Component
public class CalendarSchoolDisplayMapper {

    public CalendarEntryType toEntryType(EventType t) {
        if (t == null) {
            return CalendarEntryType.OPEN;
        }
        return switch (t) {
            case HOLIDAY, BREAK, EXAM -> CalendarEntryType.CLOSURE;
            case GRADUATION, TERM_HEADER, OBSERVATION, TENTATIVE, WEEKEND, CAMP, PREPARATION, EVENT ->
                    CalendarEntryType.MODIFIED;
            case SCHOOL_DAY -> CalendarEntryType.OPEN;
        };
    }
}
