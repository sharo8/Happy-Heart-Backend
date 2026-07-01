ALTER TABLE grace_period_requests
    ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'ATTENDANCE',
    ADD COLUMN grace_minutes INT NULL;
