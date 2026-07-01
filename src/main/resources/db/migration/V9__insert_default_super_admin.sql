-- Password: Admin@2025! (BCrypt strength 12)
INSERT INTO users (email, password, role, preferred_language, branch_id, is_active)
VALUES (
    'admin@happyhearts.rw',
    '$2b$12$W7uD7CTx/PiAPloLA9izx.3SmqXlF36sAj..3Xx9ENbT3BCE6mo9W',
    'SUPER_ADMIN',
    'EN',
    NULL,
    TRUE
);
