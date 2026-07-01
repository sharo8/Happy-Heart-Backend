CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    user_id UUID REFERENCES users (id),
    branch_id UUID NOT NULL REFERENCES branches (id),
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(50),
    category VARCHAR(50) NOT NULL,
    rfid_card_uid VARCHAR(100) UNIQUE,
    rfid_assigned_at TIMESTAMP,
    rfid_is_active BOOLEAN NOT NULL DEFAULT TRUE,
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'EN',
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT chk_employees_category CHECK (category IN (
        'LEAD_TEACHER', 'TEACHER_ASSISTANT', 'COMMUNICATION_STAFF',
        'CLEANER', 'COOK', 'SECURITY_GUARD', 'LOGISTICS', 'MANAGEMENT'
    )),
    CONSTRAINT chk_employees_lang CHECK (preferred_language IN ('EN', 'FR', 'KI'))
);

CREATE INDEX idx_employees_branch ON employees (branch_id);
CREATE INDEX idx_employees_rfid ON employees (rfid_card_uid);
CREATE INDEX idx_employees_user ON employees (user_id);
