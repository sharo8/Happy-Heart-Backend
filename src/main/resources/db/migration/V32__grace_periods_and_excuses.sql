CREATE TABLE grace_period_requests (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    employee_id UUID NOT NULL,
    requested_by_user_id UUID NULL,
    employee_reason TEXT NULL,
    approver_explanation TEXT NULL,
    granted_by_user_id UUID NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    decided_at TIMESTAMP NULL,
    email_sent_at TIMESTAMP NULL,
    CONSTRAINT fk_grace_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_grace_requested_by FOREIGN KEY (requested_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_grace_granted_by FOREIGN KEY (granted_by_user_id) REFERENCES users (id)
);

CREATE TABLE grace_period_recipients (
    grace_period_id UUID NOT NULL,
    user_id UUID NOT NULL,
    PRIMARY KEY (grace_period_id, user_id),
    CONSTRAINT fk_grace_recipient_request FOREIGN KEY (grace_period_id) REFERENCES grace_period_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_grace_recipient_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE attendance_excuses (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    employee_id UUID NOT NULL,
    granted_by_user_id UUID NOT NULL,
    excuse_type VARCHAR(50) NOT NULL,
    reason TEXT NOT NULL,
    date_from DATE NOT NULL,
    date_to DATE NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    email_sent_at TIMESTAMP NULL,
    CONSTRAINT fk_excuse_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
    CONSTRAINT fk_excuse_granted_by FOREIGN KEY (granted_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_grace_employee_dates ON grace_period_requests (employee_id, date_from, date_to);
CREATE INDEX idx_grace_status ON grace_period_requests (status);
CREATE INDEX idx_excuse_employee_dates ON attendance_excuses (employee_id, date_from, date_to);
