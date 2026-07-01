CREATE TABLE branches (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    location VARCHAR(255),
    work_start_time TIME NOT NULL DEFAULT TIME '07:30:00',
    grace_period_minutes INT NOT NULL DEFAULT 15,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX idx_branches_code ON branches (code);
