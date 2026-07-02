-- Forgot-password flow persists PASSWORD_RESET; must match Java NotificationType.PASSWORD_RESET
ALTER TABLE email_notifications DROP CONSTRAINT IF EXISTS chk_email_type;

ALTER TABLE email_notifications ADD CONSTRAINT chk_email_type CHECK (notification_type IN (
    'DAILY_REPORT', 'ABSENCE_ALERT', 'LATE_ALERT', 'WELCOME', 'RFID_ASSIGNED',
    'LOGIN_OTP', 'WELCOME_USER', 'PASSWORD_FIRST_SETUP', 'PASSWORD_RESET'
));
