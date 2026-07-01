package com.happyhearts.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CalendarEntryType {
    OPEN,
    CLOSURE,
    MODIFIED,
    SCHOOL_CLOSURE,
    HOLIDAY,
    SCHOOL_BREAK,
    BACK_TO_SCHOOL,
    EVENT,
    EXAM,
    GRADUATION,
    SUMMER_CAMP,
    PREPARATION,
    RELIGIOUS_HOLIDAY;

    @JsonCreator
    public static CalendarEntryType fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replace('-', '_').toUpperCase();
        if ("SCHOOL_CLOSURE".equals(normalized)) {
            return SCHOOL_CLOSURE;
        }
        return CalendarEntryType.valueOf(normalized);
    }
}

