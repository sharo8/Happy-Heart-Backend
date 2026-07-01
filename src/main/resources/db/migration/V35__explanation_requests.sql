CREATE TABLE IF NOT EXISTS explanation_requests (
    id CHAR(36) NOT NULL PRIMARY KEY,
    employee_id CHAR(36) NOT NULL,
    admin_id CHAR(36) NULL,
    reason TEXT NOT NULL,
    subject VARCHAR(500) NULL,
    sent_at TIMESTAMP(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT fk_explanation_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_explanation_admin FOREIGN KEY (admin_id) REFERENCES users(id)
);

CREATE INDEX idx_explanation_employee_sent ON explanation_requests (employee_id, sent_at);
