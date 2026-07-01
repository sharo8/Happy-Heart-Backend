ALTER TABLE gm_permissions
    ADD COLUMN is_locked_full BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN is_locked_view_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE feedbacks
    ADD COLUMN edit_reason VARCHAR(500) NULL;

CREATE TABLE IF NOT EXISTS feedback_edit_log (
    id CHAR(36) NOT NULL PRIMARY KEY,
    feedback_id CHAR(36) NOT NULL,
    edited_by_user_id CHAR(36) NOT NULL,
    edited_by_name VARCHAR(150) NULL,
    edit_reason VARCHAR(500) NULL,
    edited_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_feedback_edit_log_feedback FOREIGN KEY (feedback_id) REFERENCES feedbacks (id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_edit_log_user FOREIGN KEY (edited_by_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_feedback_edit_log_feedback ON feedback_edit_log (feedback_id);

DELETE gp FROM gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
WHERE gp.page_key IN ('rfid_readers', 'rfid_devices');

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_view = TRUE,
    gp.can_create = TRUE,
    gp.can_update = TRUE,
    gp.can_delete = TRUE,
    gp.can_export = TRUE,
    gp.is_locked_full = TRUE,
    gp.is_locked_view_only = FALSE
WHERE gp.page_key IN (
    'dashboard', 'notifications', 'messages', 'rfid_dashboard', 'live_feed',
    'attendance_employees', 'attendance_reports', 'branch_analytics',
    'work_schedules', 'early_departures', 'grace_periods', 'excuse_management',
    'reports', 'settings', 'audit_trail'
);

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_view = TRUE,
    gp.can_create = FALSE,
    gp.can_update = FALSE,
    gp.can_delete = FALSE,
    gp.can_export = FALSE,
    gp.is_locked_full = FALSE,
    gp.is_locked_view_only = TRUE
WHERE gp.page_key = 'branches';

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_view = TRUE,
    gp.can_create = TRUE,
    gp.can_update = FALSE,
    gp.can_delete = FALSE,
    gp.can_export = TRUE,
    gp.is_locked_full = FALSE,
    gp.is_locked_view_only = FALSE
WHERE gp.page_key = 'evaluations';
