-- Internal email inbox (Gmail-like)
CREATE TABLE IF NOT EXISTS internal_emails (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id    UUID NOT NULL REFERENCES users(id),
    to_user_id      UUID NOT NULL REFERENCES users(id),
    cc_user_ids     TEXT,
    subject         VARCHAR(500) NOT NULL,
    body            TEXT NOT NULL,
    folder          VARCHAR(30) NOT NULL DEFAULT 'inbox',
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    starred         BOOLEAN NOT NULL DEFAULT FALSE,
    label           VARCHAR(50),
    thread_id       UUID,
    reply_to_id     UUID REFERENCES internal_emails(id),
    sent_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_internal_emails_to_folder ON internal_emails (to_user_id, folder, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_internal_emails_from_folder ON internal_emails (from_user_id, folder, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_internal_emails_thread ON internal_emails (thread_id);

ALTER TABLE messages ADD COLUMN IF NOT EXISTS reply_to_id UUID REFERENCES messages(id);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS edited_at TIMESTAMPTZ;
ALTER TABLE messages ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
