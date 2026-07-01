-- Happy Hearts: expanded roles + core tables for feedbacks, internal messaging, and audit trail.
-- Intended for PostgreSQL (same style as prior migrations). If you use MySQL with ddl-auto only,
-- run the data UPDATEs manually once, then rely on Hibernate to create new tables from entities.

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(50);

UPDATE users SET role = 'CENTRAL_COORDINATOR' WHERE role = 'BRANCH_MANAGER';

UPDATE users u
SET role = v.mapped
FROM (
    SELECT u2.id AS uid,
           CASE e.category
               WHEN 'LEAD_TEACHER' THEN 'LEAD_TEACHER'
               WHEN 'TEACHER_ASSISTANT' THEN 'ASSISTANT'
               WHEN 'CLEANER' THEN 'CLEANER'
               WHEN 'COOK' THEN 'COOK'
               ELSE 'TEACHER'
           END AS mapped
    FROM users u2
    INNER JOIN employees e ON e.user_id = u2.id
    WHERE u2.role = 'EMPLOYEE'
) v
WHERE u.id = v.uid;

UPDATE users SET role = 'TEACHER' WHERE role = 'EMPLOYEE';

ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN (
    'SUPER_ADMIN',
    'GENERAL_MANAGER_PEDAGOGIQUE',
    'CENTRAL_COORDINATOR',
    'LEAD_TEACHER',
    'ASSISTANT',
    'COOK',
    'CLEANER',
    'TEACHER'
));

CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    subject VARCHAR(255),
    branch_id UUID REFERENCES branches (id),
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc')
);

CREATE INDEX idx_conversations_branch ON conversations (branch_id);

CREATE TABLE conversation_participants (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    joined_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conv_part_user ON conversation_participants (user_id);

CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users (id),
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    email_sent BOOLEAN NOT NULL DEFAULT FALSE,
    email_sent_at TIMESTAMP,
    is_read BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_messages_conversation_sent ON messages (conversation_id, sent_at);

CREATE TABLE feedbacks (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    from_user_id UUID NOT NULL REFERENCES users (id),
    to_user_id UUID NOT NULL REFERENCES users (id),
    type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    sent_by_email BOOLEAN NOT NULL DEFAULT TRUE,
    email_sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    branch_id UUID REFERENCES branches (id),
    CONSTRAINT chk_feedback_type CHECK (type IN (
        'EVALUATION', 'REMARQUE', 'FELICITATIONS', 'AVERTISSEMENT', 'RAPPORT_GMP'
    )),
    CONSTRAINT chk_feedback_visibility CHECK (visibility IN ('PRIVATE', 'SUPERIORS', 'PUBLIC'))
);

CREATE INDEX idx_feedbacks_to_created ON feedbacks (to_user_id, created_at DESC);
CREATE INDEX idx_feedbacks_from_created ON feedbacks (from_user_id, created_at DESC);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    user_id UUID REFERENCES users (id),
    action VARCHAR(100) NOT NULL,
    target_id UUID,
    target_type VARCHAR(50),
    details TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    branch_id UUID REFERENCES branches (id)
);

CREATE INDEX idx_audit_user_created ON audit_logs (user_id, created_at DESC);
CREATE INDEX idx_audit_action_created ON audit_logs (action, created_at DESC);
