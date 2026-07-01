package com.happyhearts.enums;

/**
 * Visual / semantic categories aligned with the academic calendar PDF palette.
 */
public enum EventType {
    TERM_HEADER,
    WEEKEND,
    HOLIDAY,
    EXAM,
    GRADUATION,
    OBSERVATION,
    TENTATIVE,
    BREAK,
    SCHOOL_DAY,
    /**
     * Legacy {@code calendar_events.event_type} values (pre–V29 / Flyway off).
     * Kept so Hibernate can load existing rows until data is migrated.
     */
    PREPARATION,
    CAMP,
    EVENT
}
