-- Check account email binding / password reset rollout.

SELECT
    COUNT(*) AS total_student_accounts,
    SUM(CASE WHEN email IS NOT NULL AND TRIM(email) <> '' THEN 1 ELSE 0 END) AS student_email_count,
    SUM(CASE WHEN email_verified = b'1' THEN 1 ELSE 0 END) AS verified_student_email_count
FROM student_accounts;

SELECT
    COUNT(*) AS total_teachers,
    SUM(CASE WHEN email IS NOT NULL AND TRIM(email) <> '' THEN 1 ELSE 0 END) AS teacher_email_count,
    SUM(CASE WHEN email_verified = b'1' THEN 1 ELSE 0 END) AS verified_teacher_email_count
FROM teachers;

SELECT
    COUNT(*) AS total_email_tokens,
    SUM(CASE WHEN used_at IS NULL THEN 1 ELSE 0 END) AS unused_token_count
FROM account_email_tokens;

SELECT
    COUNT(*) AS total_mail_outbox,
    SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) AS pending_mail_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed_mail_count,
    SUM(CASE WHEN template_id IS NOT NULL THEN 1 ELSE 0 END) AS templated_mail_count
FROM mail_outbox;

SELECT
    email,
    COUNT(*) AS duplicate_count
FROM (
    SELECT LOWER(TRIM(email)) AS email
    FROM student_accounts
    WHERE email IS NOT NULL AND TRIM(email) <> ''
    UNION ALL
    SELECT LOWER(TRIM(email)) AS email
    FROM teachers
    WHERE email IS NOT NULL AND TRIM(email) <> ''
) merged_emails
GROUP BY email
HAVING COUNT(*) > 1;
