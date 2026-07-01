CREATE TABLE in_app_notifications (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    body TEXT,
    read_at TIMESTAMP WITH TIME ZONE,
    kind VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX idx_in_app_notif_user_created ON in_app_notifications (user_id, created_at DESC);
CREATE INDEX idx_in_app_notif_user_unread ON in_app_notifications (user_id) WHERE read_at IS NULL;
