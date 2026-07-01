CREATE TABLE IF NOT EXISTS gm_permissions (
    id CHAR(36) NOT NULL PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    page_key VARCHAR(100) NOT NULL,
    can_view BOOLEAN NOT NULL DEFAULT TRUE,
    can_create BOOLEAN NOT NULL DEFAULT FALSE,
    can_update BOOLEAN NOT NULL DEFAULT FALSE,
    can_delete BOOLEAN NOT NULL DEFAULT FALSE,
    can_export BOOLEAN NOT NULL DEFAULT FALSE,
    granted_by CHAR(36) NULL,
    granted_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_gm_perm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_gm_perm_granter FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT uq_gm_perm_user_page UNIQUE (user_id, page_key)
);

CREATE INDEX idx_gm_permissions_user ON gm_permissions (user_id);
