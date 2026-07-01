-- Employment / HR status: inactive employees remain in DB but are flagged (e.g. cannot use facilities).
ALTER TABLE employees
    ADD COLUMN employment_active BOOLEAN NOT NULL DEFAULT TRUE;
