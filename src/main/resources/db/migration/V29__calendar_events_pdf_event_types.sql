-- Persist PDF-aligned EventType values on calendar_events (replaces legacy SchoolCalendarEventType set).

ALTER TABLE calendar_events DROP CHECK chk_calendar_events_event_type;

UPDATE calendar_events SET event_type = 'HOLIDAY' WHERE event_type = 'PREPARATION';
UPDATE calendar_events SET event_type = 'GRADUATION' WHERE event_type = 'CAMP';
UPDATE calendar_events SET event_type = 'SCHOOL_DAY' WHERE event_type = 'EVENT';

ALTER TABLE calendar_events
    ADD CONSTRAINT chk_calendar_events_event_type CHECK (event_type IN (
        'TERM_HEADER', 'WEEKEND', 'HOLIDAY', 'EXAM', 'GRADUATION',
        'OBSERVATION', 'TENTATIVE', 'BREAK', 'SCHOOL_DAY'
    ));
