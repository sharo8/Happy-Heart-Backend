-- Employee audit trail (align with branches pattern)
ALTER TABLE employees
    ADD COLUMN created_by_email VARCHAR(255) NULL,
    ADD COLUMN updated_by_email VARCHAR(255) NULL,
    ADD COLUMN updated_at TIMESTAMP NULL;

UPDATE employees SET updated_at = created_at WHERE updated_at IS NULL;
