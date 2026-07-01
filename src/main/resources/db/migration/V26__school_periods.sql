CREATE TABLE school_periods (
    id CHAR(36) NOT NULL PRIMARY KEY DEFAULT (UUID()),
    name VARCHAR(100) NOT NULL,
    school_year VARCHAR(9) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    color VARCHAR(7) DEFAULT '#FF6B35',
    branch_id CHAR(36) NULL,
    apply_to_all_branches BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by CHAR(36) NULL,
    CONSTRAINT fk_school_periods_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL,
    CONSTRAINT fk_school_periods_user FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_school_periods_year ON school_periods (school_year);
