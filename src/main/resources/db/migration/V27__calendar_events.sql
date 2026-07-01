CREATE TABLE calendar_events (
    id CHAR(36) NOT NULL PRIMARY KEY DEFAULT (UUID()),
    title VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    event_type VARCHAR(50) NOT NULL DEFAULT 'EVENT',
    is_tentative BOOLEAN NOT NULL DEFAULT FALSE,
    note TEXT NULL,
    school_year VARCHAR(9) NOT NULL,
    period_id CHAR(36) NULL,
    branch_id CHAR(36) NULL,
    apply_to_all_branches BOOLEAN NOT NULL DEFAULT TRUE,
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    pdf_import_id CHAR(36) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by CHAR(36) NULL,
    CONSTRAINT chk_calendar_events_event_type CHECK (event_type IN (
        'HOLIDAY', 'BREAK', 'SCHOOL_DAY', 'EVENT', 'EXAM', 'GRADUATION', 'CAMP', 'PREPARATION'
    )),
    CONSTRAINT chk_calendar_events_source CHECK (source IN ('PDF_IMPORT', 'MANUAL')),
    CONSTRAINT fk_calendar_events_period FOREIGN KEY (period_id) REFERENCES school_periods (id) ON DELETE SET NULL,
    CONSTRAINT fk_calendar_events_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT fk_calendar_events_pdf_import FOREIGN KEY (pdf_import_id) REFERENCES pdf_imports (id) ON DELETE SET NULL,
    CONSTRAINT fk_calendar_events_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_calendar_events_school_year ON calendar_events (school_year);
CREATE INDEX idx_calendar_events_dates ON calendar_events (start_date, end_date);
CREATE INDEX idx_calendar_events_branch_scope ON calendar_events (apply_to_all_branches, branch_id);
