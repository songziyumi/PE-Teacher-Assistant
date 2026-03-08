-- 为 students(school_id, student_no) 补齐唯一索引（幂等）
-- 执行前会先检查重复数据，存在重复则不做 DDL 变更。
--
-- 用法示例：
-- mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/ensure_students_unique_index.sql

SET @schema_name = DATABASE();

-- 1) 重复数据统计：存在重复时，必须先人工清理
SELECT COUNT(*) INTO @dup_group_count
FROM (
    SELECT 1
    FROM students
    WHERE student_no IS NOT NULL
      AND TRIM(student_no) <> ''
    GROUP BY school_id, student_no
    HAVING COUNT(*) > 1
) dup_groups;

-- 2) 是否已存在“school_id,student_no”唯一索引（不依赖索引名）
SELECT COUNT(*) INTO @idx_exists
FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'students'
      AND non_unique = 0
    GROUP BY index_name
    HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'school_id,student_no'
) uniq_idx;

-- 3) 动态执行：有重复 -> ABORT；已存在 -> SKIP；不存在 -> ADD UNIQUE
SET @ddl = IF(
    @dup_group_count > 0,
    'SELECT ''ABORT: duplicated (school_id, student_no) exists, please deduplicate first.'' AS result',
    IF(
        @idx_exists > 0,
        'SELECT ''SKIP: unique index already exists on (school_id, student_no).'' AS result',
        'ALTER TABLE students ADD CONSTRAINT uk_students_school_student_no UNIQUE (school_id, student_no)'
    )
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4) 收尾核验：输出最新索引信息
SELECT
    index_name,
    non_unique,
    GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'students'
GROUP BY index_name, non_unique
ORDER BY index_name;
