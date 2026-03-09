# students 唯一索引核验与补齐

目标：确保数据库存在唯一索引 `students(school_id, student_no)`，防止并发脏写。

## 文件

- `scripts/db/check_students_unique_index.sql`
  - 只读核验：重复数据 + 索引状态
- `scripts/db/ensure_students_unique_index.sql`
  - 幂等修复：自动判断 `ABORT / SKIP / ADD UNIQUE`

## 执行顺序

1. 先执行核验脚本，确认重复数据为 0 行。
2. 再执行修复脚本补索引（或确认已存在）。
3. 最后再次执行核验脚本，确认状态为 `OK`。

## Windows 本机示例

```powershell
$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$db = "pe_assistant"
$root = "C:/Users/28970/Desktop/code/PE_TEACHER_ASSISTANT_JAVA"

# 1) 核验
& $mysql -h"127.0.0.1" -P"3306" -uroot -p -D $db --execute="source $root/scripts/db/check_students_unique_index.sql"

# 2) 修复（幂等）
& $mysql -h"127.0.0.1" -P"3306" -uroot -p -D $db --execute="source $root/scripts/db/ensure_students_unique_index.sql"

# 3) 二次核验
& $mysql -h"127.0.0.1" -P"3306" -uroot -p -D $db --execute="source $root/scripts/db/check_students_unique_index.sql"
```

## Linux 服务器示例（部署机）

```bash
cd /opt/pe-assistant

mysql -h127.0.0.1 -P3306 -upe_user -p pe_assistant < scripts/db/check_students_unique_index.sql
mysql -h127.0.0.1 -P3306 -upe_user -p pe_assistant < scripts/db/ensure_students_unique_index.sql
mysql -h127.0.0.1 -P3306 -upe_user -p pe_assistant < scripts/db/check_students_unique_index.sql
```

## 结果判定

- 核验脚本第 1 段（重复数据查询）应返回 `0` 行。
- 核验脚本最后一段 `unique_index_status` 应为 `OK`。
- 若返回 `MISSING`，执行修复脚本后应变为 `OK`。
- 若修复脚本输出 `ABORT`，先清理重复学号再重试。
