-- OTP step after password validation (login)
CREATE TABLE login_otp_challenges (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    failed_attempts INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX idx_login_otp_user ON login_otp_challenges (user_id, expires_at);
CREATE INDEX idx_login_otp_expires ON login_otp_challenges (expires_at) WHERE used = FALSE;

-- First-time password setup (admin-created accounts)
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS initial_setup_token VARCHAR(128) UNIQUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS initial_setup_token_expires_at TIMESTAMP;

-- Extend notification types for auth / onboarding emails
ALTER TABLE email_notifications DROP CONSTRAINT IF EXISTS chk_email_type;
ALTER TABLE email_notifications ADD CONSTRAINT chk_email_type CHECK (notification_type IN (
    'DAILY_REPORT', 'ABSENCE_ALERT', 'LATE_ALERT', 'WELCOME', 'RFID_ASSIGNED',
    'LOGIN_OTP', 'WELCOME_USER', 'PASSWORD_FIRST_SETUP'
));
