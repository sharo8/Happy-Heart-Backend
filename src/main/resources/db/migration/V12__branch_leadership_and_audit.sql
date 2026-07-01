ALTER TABLE branches
    ADD COLUMN lead_teacher_id UUID NULL,
    ADD COLUMN second_teacher_id UUID NULL,
    ADD COLUMN created_by_email VARCHAR(255) NULL,
    ADD COLUMN updated_by_email VARCHAR(255) NULL,
    ADD COLUMN updated_at TIMESTAMP NULL;

ALTER TABLE branches
    ADD CONSTRAINT fk_branches_lead_teacher
        FOREIGN KEY (lead_teacher_id) REFERENCES employees(id),
    ADD CONSTRAINT fk_branches_second_teacher
        FOREIGN KEY (second_teacher_id) REFERENCES employees(id);

CREATE INDEX idx_branches_lead_teacher_id ON branches(lead_teacher_id);
CREATE INDEX idx_branches_second_teacher_id ON branches(second_teacher_id);
