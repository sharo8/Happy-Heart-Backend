CREATE TABLE calendar_days (
    id CHAR(36) NOT NULL PRIMARY KEY DEFAULT (UUID()),
    day_date DATE NOT NULL,
    school_year VARCHAR(9) NOT NULL,
    day_kind VARCHAR(20) NOT NULL,
    color_bg VARCHAR(7) NULL,
    color_text VARCHAR(7) NULL,
    label VARCHAR(255) NOT NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'PDF_IMPORT',
    calendar_event_id CHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_calendar_days_event FOREIGN KEY (calendar_event_id) REFERENCES calendar_events (id) ON DELETE CASCADE,
    CONSTRAINT chk_calendar_days_day_kind CHECK (day_kind IN ('OPEN', 'CLOSURE', 'MODIFIED')),
    CONSTRAINT chk_calendar_days_source CHECK (source IN ('PDF_IMPORT', 'MANUAL')),
    CONSTRAINT uk_calendar_days_event_date UNIQUE (calendar_event_id, day_date)
);

CREATE INDEX idx_calendar_days_day_date ON calendar_days (day_date);
CREATE INDEX idx_calendar_days_school_year ON calendar_days (school_year);
