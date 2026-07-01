CREATE TABLE daily_attendance_summary (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    employee_id UUID NOT NULL REFERENCES employees (id),
    branch_id UUID NOT NULL REFERENCES branches (id),
    summary_date DATE NOT NULL,
    entry_time TIME,
    exit_time TIME,
    total_hours DECIMAL(5, 2),
    status VARCHAR(20) NOT NULL,
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    late_minutes INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT uq_daily_summary_employee_date UNIQUE (employee_id, summary_date),
    CONSTRAINT chk_daily_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'HALF_DAY'))
);

CREATE INDEX idx_daily_summary_branch_date ON daily_attendance_summary (branch_id, summary_date);
