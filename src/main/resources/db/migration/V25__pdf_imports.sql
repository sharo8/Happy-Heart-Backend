CREATE TABLE pdf_imports (
    id CHAR(36) NOT NULL PRIMARY KEY DEFAULT (UUID()),
    filename VARCHAR(255) NOT NULL,
    school_year VARCHAR(9) NOT NULL,
    imported_by CHAR(36) NULL,
    apply_to_all_branches BOOLEAN NOT NULL DEFAULT TRUE,
    branch_id CHAR(36) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    events_created INT NOT NULL DEFAULT 0,
    periods_created INT NOT NULL DEFAULT 0,
    raw_text TEXT NULL,
    parsed_data JSON NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT chk_pdf_imports_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED')),
    CONSTRAINT fk_pdf_imports_user FOREIGN KEY (imported_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_pdf_imports_branch FOREIGN KEY (branch_id) REFERENCES branches (id) ON DELETE SET NULL
);

CREATE INDEX idx_pdf_imports_school_year ON pdf_imports (school_year);
CREATE INDEX idx_pdf_imports_status ON pdf_imports (status);
