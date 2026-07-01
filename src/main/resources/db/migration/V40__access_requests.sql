CREATE TABLE access_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_message TEXT,
    assigned_role VARCHAR(50),
    assigned_branch_id UUID REFERENCES branches(id),
    reviewed_by UUID REFERENCES users(id),
    reviewed_at TIMESTAMPTZ,
    created_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_access_requests_status ON access_requests(status);
CREATE INDEX idx_access_requests_email ON access_requests(LOWER(email));
CREATE INDEX idx_access_requests_created_at ON access_requests(created_at DESC);
