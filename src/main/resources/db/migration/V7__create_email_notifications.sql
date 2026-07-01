CREATE TABLE email_notifications (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    language VARCHAR(5) NOT NULL,
    notification_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT chk_email_lang CHECK (language IN ('EN', 'FR', 'KI')),
    CONSTRAINT chk_email_type CHECK (notification_type IN (
        'DAILY_REPORT', 'ABSENCE_ALERT', 'LATE_ALERT', 'WELCOME', 'RFID_ASSIGNED'
    )),
    CONSTRAINT chk_email_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_email_notifications_status ON email_notifications (status, created_at);
