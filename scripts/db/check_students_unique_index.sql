-- 检查 students(school_id, student_no) 唯一索引与重复数据
-- 用法示例：
-- mysql -h127.0.0.1 -P3306 -uroot -p pe_assistant < scripts/db/check_students_unique_index.sql

SELECT DATABASE() AS current_schema;

-- 1) 重复数据检查（建唯一索引前必须为 0 行）
SELECT school_id, student_no, COUNT(*) AS dup_count
FROM students
WHERE student_no IS NOT NULL
  AND TRIM(student_no) <> ''
GROUP BY school_id, student_no
HAVING COUNT(*) > 1
ORDER BY dup_count DESC, school_id, student_no
LIMIT 200;

-- 2) students 表现有索引概览
SELECT
    index_name,
    non_unique,
    GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns_in_index
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'students'
GROUP BY index_name, non_unique
ORDER BY index_name;

-- 3) 目标唯一索引状态（OK / MISSING）
SELECT CASE
         WHEN EXISTS (
           SELECT 1
           FROM (
             SELECT index_name
             FROM information_schema.statistics
             WHERE table_schema = DATABASE()
               AND table_name = 'students'
               AND non_unique = 0
             GROUP BY index_name
             HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'school_id,student_no'
           ) idx
         ) THEN 'OK'
         ELSE 'MISSING'
       END AS unique_index_status;
