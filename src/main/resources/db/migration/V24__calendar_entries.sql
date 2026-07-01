CREATE TABLE calendar_entries (
    id CHAR(36) NOT NULL PRIMARY KEY DEFAULT (UUID()),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    entry_type VARCHAR(30) NOT NULL,
    label_en TEXT NULL,
    label_fr TEXT NULL,
    label_ki TEXT NULL,
    applies_to_all BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_calendar_entries_start_end ON calendar_entries (start_date, end_date);
CREATE INDEX idx_calendar_entries_type ON calendar_entries (entry_type);

CREATE TABLE calendar_entry_branches (
    calendar_entry_id CHAR(36) NOT NULL,
    branch_id CHAR(36) NOT NULL,
    PRIMARY KEY (calendar_entry_id, branch_id),
    CONSTRAINT fk_calendar_entry_branches_entry
        FOREIGN KEY (calendar_entry_id) REFERENCES calendar_entries (id) ON DELETE CASCADE,
    CONSTRAINT fk_calendar_entry_branches_branch
        FOREIGN KEY (branch_id) REFERENCES branches (id)
);

