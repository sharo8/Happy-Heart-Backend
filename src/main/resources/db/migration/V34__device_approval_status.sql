ALTER TABLE devices
    ADD COLUMN approval_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

UPDATE devices SET approval_status = 'ACTIVE' WHERE approval_status IS NULL;
