-- Add account email binding / password reset support.
-- Safe to rerun on MySQL 8+.

SET @schema_name = DATABASE();

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND COLUMN_NAME = 'email'),
    'SELECT ''student_accounts.email exists''',
    'ALTER TABLE student_accounts ADD COLUMN email VARCHAR(100) NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND COLUMN_NAME = 'email_verified'),
    'SELECT ''student_accounts.email_verified exists''',
    'ALTER TABLE student_accounts ADD COLUMN email_verified BIT NOT NULL DEFAULT b''0''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND COLUMN_NAME = 'email_bound_at'),
    'SELECT ''student_accounts.email_bound_at exists''',
    'ALTER TABLE student_accounts ADD COLUMN email_bound_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND COLUMN_NAME = 'email_verified_at'),
    'SELECT ''student_accounts.email_verified_at exists''',
    'ALTER TABLE student_accounts ADD COLUMN email_verified_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND COLUMN_NAME = 'email_notify_enabled'),
    'SELECT ''student_accounts.email_notify_enabled exists''',
    'ALTER TABLE student_accounts ADD COLUMN email_notify_enabled BIT NOT NULL DEFAULT b''1''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'student_accounts' AND INDEX_NAME = 'idx_student_account_email'),
    'SELECT ''idx_student_account_email exists''',
    'CREATE INDEX idx_student_account_email ON student_accounts (email)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'teachers' AND COLUMN_NAME = 'email_verified'),
    'SELECT ''teachers.email_verified exists''',
    'ALTER TABLE teachers ADD COLUMN email_verified BIT NOT NULL DEFAULT b''0''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'teachers' AND COLUMN_NAME = 'email_bound_at'),
    'SELECT ''teachers.email_bound_at exists''',
    'ALTER TABLE teachers ADD COLUMN email_bound_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'teachers' AND COLUMN_NAME = 'email_verified_at'),
    'SELECT ''teachers.email_verified_at exists''',
    'ALTER TABLE teachers ADD COLUMN email_verified_at DATETIME NULL');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'teachers' AND COLUMN_NAME = 'email_notify_enabled'),
    'SELECT ''teachers.email_notify_enabled exists''',
    'ALTER TABLE teachers ADD COLUMN email_notify_enabled BIT NOT NULL DEFAULT b''1''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'teachers' AND INDEX_NAME = 'idx_teacher_email'),
    'SELECT ''idx_teacher_email exists''',
    'CREATE INDEX idx_teacher_email ON teachers (email)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS account_email_tokens (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purpose VARCHAR(20) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id BIGINT NOT NULL,
    target_email VARCHAR(100) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    expires_at DATETIME NOT NULL,
    used_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    request_ip VARCHAR(45) NULL,
    user_agent VARCHAR(255) NULL,
    CONSTRAINT uk_account_email_token_hash UNIQUE (token_hash)
);

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account_email_tokens'
             AND INDEX_NAME = 'idx_account_email_token_principal'),
    'SELECT ''idx_account_email_token_principal exists''',
    'CREATE INDEX idx_account_email_token_principal ON account_email_tokens (principal_type, principal_id, purpose)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'account_email_tokens'
             AND INDEX_NAME = 'idx_account_email_token_expires'),
    'SELECT ''idx_account_email_token_expires exists''',
    'CREATE INDEX idx_account_email_token_expires ON account_email_tokens (expires_at)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS mail_outbox (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    biz_type VARCHAR(30) NOT NULL,
    principal_type VARCHAR(20) NOT NULL,
    principal_id BIGINT NOT NULL,
    recipient_email VARCHAR(100) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    template_id BIGINT NULL,
    template_data TEXT NULL,
    body_text TEXT NULL,
    body_html TEXT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    next_retry_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    sent_at DATETIME NULL,
    provider_message_id VARCHAR(120) NULL,
    provider_request_id VARCHAR(120) NULL
);

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox' AND COLUMN_NAME = 'template_id'),
    'SELECT ''mail_outbox.template_id exists''',
    'ALTER TABLE mail_outbox ADD COLUMN template_id BIGINT NULL AFTER subject');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox' AND COLUMN_NAME = 'template_data'),
    'SELECT ''mail_outbox.template_data exists''',
    'ALTER TABLE mail_outbox ADD COLUMN template_data TEXT NULL AFTER template_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox' AND COLUMN_NAME = 'provider_message_id'),
    'SELECT ''mail_outbox.provider_message_id exists''',
    'ALTER TABLE mail_outbox ADD COLUMN provider_message_id VARCHAR(120) NULL AFTER sent_at');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox' AND COLUMN_NAME = 'provider_request_id'),
    'SELECT ''mail_outbox.provider_request_id exists''',
    'ALTER TABLE mail_outbox ADD COLUMN provider_request_id VARCHAR(120) NULL AFTER provider_message_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox'
             AND INDEX_NAME = 'idx_mail_outbox_status'),
    'SELECT ''idx_mail_outbox_status exists''',
    'CREATE INDEX idx_mail_outbox_status ON mail_outbox (status, next_retry_at)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
    EXISTS(SELECT 1 FROM information_schema.STATISTICS
           WHERE TABLE_SCHEMA = @schema_name AND TABLE_NAME = 'mail_outbox'
             AND INDEX_NAME = 'idx_mail_outbox_principal'),
    'SELECT ''idx_mail_outbox_principal exists''',
    'CREATE INDEX idx_mail_outbox_principal ON mail_outbox (principal_type, principal_id)');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
