ALTER TABLE branches
    ADD COLUMN work_end_time TIME NULL DEFAULT TIME '17:00:00';

UPDATE branches SET work_end_time = TIME '17:00:00' WHERE work_end_time IS NULL;

ALTER TABLE employees
    ADD COLUMN use_branch_schedule BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN work_start_time TIME NULL,
    ADD COLUMN work_end_time TIME NULL,
    ADD COLUMN grace_period_minutes INT NULL;
