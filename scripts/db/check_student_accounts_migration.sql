-- Validate student account rollout status.
-- Usage:
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/check_student_accounts_migration.sql

SET @schema_name = DATABASE();

SELECT
    COUNT(*) AS total_students,
    SUM(CASE WHEN password IS NOT NULL AND TRIM(password) <> '' THEN 1 ELSE 0 END) AS legacy_password_students,
    SUM(CASE WHEN enabled = 0 THEN 1 ELSE 0 END) AS legacy_disabled_students
FROM students;

SELECT
    COUNT(*) AS student_accounts_table_exists
FROM information_schema.tables
WHERE table_schema = @schema_name
  AND table_name = 'student_accounts';

SELECT
    COUNT(*) AS total_student_accounts,
    SUM(CASE WHEN password_hash IS NULL OR TRIM(password_hash) = '' THEN 1 ELSE 0 END) AS missing_password_hash,
    SUM(CASE WHEN login_id IS NULL OR TRIM(login_id) = '' THEN 1 ELSE 0 END) AS missing_login_id,
    SUM(CASE WHEN enabled = 0 THEN 1 ELSE 0 END) AS disabled_accounts
FROM student_accounts;

SELECT
    COUNT(*) AS students_without_account
FROM students s
LEFT JOIN student_accounts sa ON sa.student_id = s.id
WHERE sa.id IS NULL;

SELECT
    COUNT(*) AS duplicate_student_bindings
FROM (
    SELECT student_id
    FROM student_accounts
    GROUP BY student_id
    HAVING COUNT(*) > 1
) t;

SELECT
    COUNT(*) AS duplicate_login_ids
FROM (
    SELECT UPPER(login_id)
    FROM student_accounts
    GROUP BY UPPER(login_id)
    HAVING COUNT(*) > 1
) t;
