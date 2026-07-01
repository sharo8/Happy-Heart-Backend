CREATE TABLE announcements (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    title VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    send_to_all BOOLEAN NOT NULL DEFAULT FALSE,
    target_roles VARCHAR(200) NULL,
    created_by_email VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX idx_announcements_created ON announcements (created_at DESC);
