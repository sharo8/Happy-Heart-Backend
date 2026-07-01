-- Align calendar_events with product spec: persisted colors, optional period label, event types HOLIDAY..EVENT (no SCHOOL_DAY).

UPDATE calendar_events
SET event_type = 'EVENT'
WHERE event_type = 'SCHOOL_DAY';

ALTER TABLE calendar_events DROP CHECK chk_calendar_events_event_type;

ALTER TABLE calendar_events
    ADD CONSTRAINT chk_calendar_events_event_type CHECK (event_type IN (
        'HOLIDAY', 'BREAK', 'GRADUATION', 'CAMP', 'EXAM', 'PREPARATION', 'EVENT'
    ));

ALTER TABLE calendar_events
    MODIFY COLUMN event_type VARCHAR(20) NOT NULL DEFAULT 'EVENT';

ALTER TABLE calendar_events
    ADD COLUMN color_bg VARCHAR(7) NULL AFTER event_type,
    ADD COLUMN color_text VARCHAR(7) NULL AFTER color_bg;

ALTER TABLE calendar_events
    ADD COLUMN period_name VARCHAR(50) NULL AFTER period_id;
