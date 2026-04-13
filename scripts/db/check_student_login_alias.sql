-- Check student login alias rollout status.
-- Run example:
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/check_student_login_alias.sql

SELECT
    COUNT(*) AS total_student_accounts,
    SUM(CASE WHEN login_alias IS NOT NULL AND TRIM(login_alias) <> '' THEN 1 ELSE 0 END) AS bound_login_alias_count
FROM student_accounts;

SELECT
    login_alias,
    COUNT(*) AS duplicate_count
FROM student_accounts
WHERE login_alias IS NOT NULL
  AND TRIM(login_alias) <> ''
GROUP BY login_alias
HAVING COUNT(*) > 1;
