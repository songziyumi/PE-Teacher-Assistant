-- 批量激活测试学生账号（默认针对学号前缀 CS26 的测试数据）
--
-- 用途：
-- 1) 跳过“首次登录必须修改密码”的限制
-- 2) 让 JMeter / 联调测试可直接使用当前初始密码登录
--
-- 默认范围：
-- - students.student_no LIKE 'CS26%'
-- - 如需限制学校，可设置 @school_id
--
-- 使用方式：
--   mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/activate_test_student_accounts.sql

SET @school_id = NULL;
SET @student_no_prefix = 'CS26%';

-- 先查看将被激活的账号
SELECT
    sa.id,
    sa.login_id,
    s.student_no,
    s.name,
    sa.enabled,
    sa.locked,
    sa.activated,
    sa.password_reset_required
FROM student_accounts sa
JOIN students s ON s.id = sa.student_id
WHERE s.student_no LIKE @student_no_prefix
  AND (@school_id IS NULL OR s.school_id = @school_id)
ORDER BY s.student_no;

-- 批量激活
UPDATE student_accounts sa
JOIN students s ON s.id = sa.student_id
SET sa.enabled = 1,
    sa.locked = 0,
    sa.activated = 1,
    sa.password_reset_required = 0,
    sa.failed_attempts = 0,
    sa.locked_until = NULL,
    sa.updated_at = NOW()
WHERE s.student_no LIKE @student_no_prefix
  AND (@school_id IS NULL OR s.school_id = @school_id);

-- 查看结果
SELECT
    COUNT(*) AS activated_count
FROM student_accounts sa
JOIN students s ON s.id = sa.student_id
WHERE s.student_no LIKE @student_no_prefix
  AND (@school_id IS NULL OR s.school_id = @school_id)
  AND sa.enabled = 1
  AND sa.locked = 0
  AND sa.activated = 1
  AND sa.password_reset_required = 0;
