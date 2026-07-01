-- Extended announcements: audience, scheduling, email meta, read tracking

ALTER TABLE announcements
    ADD COLUMN priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN audience_type VARCHAR(30) NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN target_branch_ids TEXT NULL,
    ADD COLUMN target_categories TEXT NULL,
    ADD COLUMN send_immediately BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN scheduled_at TIMESTAMP NULL,
    ADD COLUMN expires_at TIMESTAMP NULL,
    ADD COLUMN email_notification BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN email_subject VARCHAR(500) NULL,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    ADD COLUMN email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN created_by_user_id UUID NULL REFERENCES users (id),
    ADD COLUMN recipient_count INT NOT NULL DEFAULT 0;

UPDATE announcements
SET audience_type = CASE WHEN send_to_all THEN 'EVERYONE' ELSE 'ROLE' END
WHERE TRUE;

CREATE TABLE announcement_reads (
    announcement_id UUID NOT NULL REFERENCES announcements (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    PRIMARY KEY (announcement_id, user_id)
);

CREATE INDEX idx_announcement_reads_user ON announcement_reads (user_id);
