-- Backfill student_accounts for students that do not yet have one.
-- This script is intentionally conservative:
-- 1) It creates deterministic login IDs based on student_id.
-- 2) It preserves legacy password text in issued_password when present.
-- 3) It marks every migrated account as password_reset_required so an admin
--    can regenerate or reset passwords through the application afterwards.
--
-- Usage:
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/migrate_student_accounts.sql

CREATE TABLE IF NOT EXISTS student_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    student_id BIGINT NOT NULL,
    login_id VARCHAR(32) NOT NULL,
    password_hash VARCHAR(200) NOT NULL,
    enabled BIT(1) NOT NULL DEFAULT b'1',
    locked BIT(1) NOT NULL DEFAULT b'0',
    activated BIT(1) NOT NULL DEFAULT b'0',
    password_reset_required BIT(1) NOT NULL DEFAULT b'1',
    last_login_at DATETIME NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME NULL,
    issued_password VARCHAR(50) NULL,
    last_password_reset_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_student_account_student UNIQUE (student_id),
    CONSTRAINT uk_student_account_login_id UNIQUE (login_id)
);

INSERT INTO student_accounts (
    student_id,
    login_id,
    password_hash,
    enabled,
    locked,
    activated,
    password_reset_required,
    last_login_at,
    failed_attempts,
    locked_until,
    issued_password,
    last_password_reset_at,
    created_at,
    updated_at
)
SELECT
    s.id,
    CONCAT('LEGACY', LPAD(s.id, 8, '0')),
    CONCAT('MIGRATION_PENDING_', s.id),
    COALESCE(s.enabled, 1),
    0,
    0,
    1,
    NULL,
    0,
    NULL,
    CASE
        WHEN s.password IS NULL OR TRIM(s.password) = '' THEN NULL
        ELSE LEFT(s.password, 50)
    END,
    NOW(),
    NOW(),
    NOW()
FROM students s
LEFT JOIN student_accounts sa ON sa.student_id = s.id
WHERE sa.id IS NULL;
