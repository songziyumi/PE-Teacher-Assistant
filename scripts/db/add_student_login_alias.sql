-- Add student login alias columns for friendly login rollout.
-- Run example:
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/add_student_login_alias.sql

ALTER TABLE student_accounts
    ADD COLUMN login_alias VARCHAR(32) NULL,
    ADD COLUMN login_alias_bound_at DATETIME NULL;

CREATE UNIQUE INDEX uk_student_account_login_alias
    ON student_accounts (login_alias);
