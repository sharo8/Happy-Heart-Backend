CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    preferred_language VARCHAR(5) NOT NULL DEFAULT 'EN',
    branch_id UUID REFERENCES branches (id),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT chk_users_role CHECK (role IN ('SUPER_ADMIN', 'BRANCH_MANAGER', 'EMPLOYEE')),
    CONSTRAINT chk_users_lang CHECK (preferred_language IN ('EN', 'FR', 'KI'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_branch ON users (branch_id);
