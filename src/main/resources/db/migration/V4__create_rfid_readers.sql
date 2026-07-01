CREATE TABLE rfid_readers (
    id UUID PRIMARY KEY DEFAULT (UUID()),
    branch_id UUID NOT NULL REFERENCES branches (id),
    reader_code VARCHAR(100) NOT NULL UNIQUE,
    reader_type VARCHAR(20) NOT NULL,
    location_description VARCHAR(255),
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    last_sync_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT (NOW() AT TIME ZONE 'utc'),
    CONSTRAINT chk_rfid_readers_type CHECK (reader_type IN ('ENTRY', 'EXIT'))
);

CREATE INDEX idx_rfid_readers_branch ON rfid_readers (branch_id);
