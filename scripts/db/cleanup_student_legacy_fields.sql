-- Clear legacy students.password data after student_accounts have been issued
-- and verified. Run this only after admins have regenerated/reset passwords
-- for migrated placeholder accounts.
--
-- Usage:
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/cleanup_student_legacy_fields.sql

UPDATE students
SET password = NULL;

-- enabled is intentionally left in place for now because the column still
-- exists on the entity/table. The application no longer uses it for student
-- authentication decisions.
