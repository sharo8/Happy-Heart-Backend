CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    employee_id UUID NOT NULL REFERENCES employees (id),
    branch_id UUID NOT NULL REFERENCES branches (id),
    rfid_reader_id UUID NOT NULL REFERENCES rfid_readers (id),
    rfid_card_uid VARCHAR(100) NOT NULL,
    scan_type VARCHAR(20) NOT NULL,
    scanned_at TIMESTAMP NOT NULL,
    is_synced BOOLEAN NOT NULL DEFAULT FALSE,
    sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT chk_attendance_scan_type CHECK (scan_type IN ('ENTRY', 'EXIT'))
);

CREATE INDEX idx_attendance_employee_date ON attendance_records (employee_id, scanned_at);
CREATE INDEX idx_attendance_branch_date ON attendance_records (branch_id, scanned_at);
CREATE INDEX idx_attendance_card_time ON attendance_records (rfid_card_uid, scanned_at);
CREATE INDEX idx_attendance_reader ON attendance_records (rfid_reader_id);
