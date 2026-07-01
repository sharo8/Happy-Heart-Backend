ALTER TABLE feedbacks
    ADD COLUMN updated_at TIMESTAMP(6) NULL,
    ADD COLUMN updated_by_user_id CHAR(36) NULL,
    ADD CONSTRAINT fk_feedback_updated_by FOREIGN KEY (updated_by_user_id) REFERENCES users (id) ON DELETE SET NULL;

-- Standard GMP grants requested by product owner
UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_create = TRUE
WHERE gp.page_key = 'messages';

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_create = TRUE,
    gp.can_update = TRUE,
    gp.can_delete = TRUE,
    gp.can_export = TRUE
WHERE gp.page_key = 'evaluations';

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_export = TRUE
WHERE gp.page_key IN ('attendance_reports', 'branch_analytics');

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_create = TRUE,
    gp.can_update = TRUE,
    gp.can_delete = TRUE,
    gp.can_export = TRUE
WHERE gp.page_key = 'work_schedules';

UPDATE gm_permissions gp
    INNER JOIN users u ON u.id = gp.user_id AND u.role = 'GENERAL_MANAGER_PEDAGOGIQUE'
SET gp.can_create = FALSE,
    gp.can_update = FALSE,
    gp.can_delete = FALSE,
    gp.can_export = FALSE
WHERE gp.page_key = 'users';
