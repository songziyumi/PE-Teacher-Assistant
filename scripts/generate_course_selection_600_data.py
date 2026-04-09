from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

from openpyxl import Workbook


ROOT = Path(__file__).resolve().parent
OUTPUT_DIR = ROOT / "testdata" / "course-selection-600"

GRADE_NAME = "高一"
STUDENT_PREFIX = "CS26"
EVENT_NAME = "2026高一600人抽签测试活动"
DEFAULT_TEACHER_PASSWORD = "123456"


@dataclass(frozen=True)
class CourseDef:
    name: str
    capacity_mode: str
    total_capacity: int
    teacher_name: str
    per_class_capacity: int = 0


COURSES = [
    CourseDef("篮球基础", "GLOBAL", 56, "选课测试教师01"),
    CourseDef("足球训练", "GLOBAL", 52, "选课测试教师02"),
    CourseDef("羽毛球提高", "GLOBAL", 58, "选课测试教师03"),
    CourseDef("排球技巧", "GLOBAL", 54, "选课测试教师04"),
    CourseDef("乒乓球进阶", "GLOBAL", 50, "选课测试教师05"),
    CourseDef("健美操", "GLOBAL", 48, "选课测试教师06"),
    CourseDef("武术入门", "GLOBAL", 60, "选课测试教师07"),
    CourseDef("田径提升", "PER_CLASS", 48, "选课测试教师08", 4),
    CourseDef("跳绳挑战", "PER_CLASS", 48, "选课测试教师09", 4),
]


def build_admin_classes() -> list[dict[str, str]]:
    return [{"班级名称": f"{i}班", "类型": "行政班", "年级": GRADE_NAME} for i in range(1, 13)]


def build_students() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    statuses = ["在籍"] * 600
    for class_index in range(12):
        class_name = f"{class_index + 1}班"
        for offset in range(50):
            student_index = class_index * 50 + offset + 1
            student_no = f"{STUDENT_PREFIX}{student_index:04d}"
            gender = "男" if student_index % 2 else "女"
            id_card = f"3201012008{((student_index - 1) % 12) + 1:02d}{((student_index - 1) % 28) + 1:02d}{student_index:04d}"
            rows.append(
                {
                    "年级": GRADE_NAME,
                    "班级": class_name,
                    "姓名": f"测试学生{student_index:04d}",
                    "性别": gender,
                    "学号": student_no,
                    "身份证号": id_card,
                    "选修课": "",
                    "学籍状态": statuses[student_index - 1],
                }
            )
    return rows


def build_teachers() -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    for index, course in enumerate(COURSES, start=1):
        rows.append(
            {
                "姓名": course.teacher_name,
                "手机号": f"1390000{index:04d}",
                "密码": DEFAULT_TEACHER_PASSWORD,
                "行政班": "",
                "选修课": "",
                "账号类型": "教师",
                "组织管理员类型": "",
            }
        )
    return rows


def build_preferences(students: list[dict[str, str]]) -> list[tuple[str, str, int, str]]:
    selections: list[tuple[str, str, int, str]] = []
    base_time = datetime(2026, 4, 1, 8, 0, 0)

    for idx, student in enumerate(students):
        class_no = (idx // 50) + 1
        first_idx = (idx * 7 + class_no * 3) % len(COURSES)
        second_shift = 2 + (idx % 5)
        second_idx = (first_idx + second_shift) % len(COURSES)
        if second_idx == first_idx:
            second_idx = (second_idx + 1) % len(COURSES)

        first_time = (base_time + timedelta(seconds=idx * 2)).strftime("%Y-%m-%d %H:%M:%S")
        second_time = (base_time + timedelta(seconds=idx * 2 + 1)).strftime("%Y-%m-%d %H:%M:%S")
        selections.append((student["学号"], COURSES[first_idx].name, 1, first_time))
        selections.append((student["学号"], COURSES[second_idx].name, 2, second_time))

    return selections


def write_xlsx(path: Path, sheet_name: str, columns: list[str], rows: list[dict[str, str]]) -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = sheet_name
    ws.append(columns)
    for row in rows:
        ws.append([row.get(col, "") for col in columns])
    for column in ws.columns:
        max_length = max(len(str(cell.value or "")) for cell in column)
        ws.column_dimensions[column[0].column_letter].width = min(max(max_length + 2, 10), 24)
    wb.save(path)


def write_courses_csv(path: Path) -> None:
    lines = ["课程名称,授课教师,名额模式,总名额,按班名额说明"]
    for course in COURSES:
        per_class_note = "" if course.capacity_mode == "GLOBAL" else f"每个行政班 {course.per_class_capacity} 人"
        lines.append(f"{course.name},{course.teacher_name},{course.capacity_mode},{course.total_capacity},{per_class_note}")
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def chunked(items: list[str], size: int) -> list[list[str]]:
    return [items[i:i + size] for i in range(0, len(items), size)]


def sql_literal(value: str) -> str:
    return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def write_sql(path: Path, admin_classes: list[dict[str, str]], preferences: list[tuple[str, str, int, str]]) -> None:
    course_values = ",\n".join(
        f"  ({sql_literal(course.name)}, {sql_literal(course.teacher_name)}, {sql_literal(course.capacity_mode)}, {course.total_capacity})"
        for course in COURSES
    )
    per_class_course_names = ", ".join(sql_literal(course.name) for course in COURSES if course.capacity_mode == "PER_CLASS")
    class_names = ", ".join(sql_literal(item["班级名称"]) for item in admin_classes)

    selection_rows: list[str] = []
    for student_no, course_name, preference, selected_at in preferences:
        selection_rows.append(
            f"  ({sql_literal(student_no)}, {sql_literal(course_name)}, {preference}, {sql_literal(selected_at)})"
        )

    selection_blocks = []
    for block in chunked(selection_rows, 200):
        selection_blocks.append(
            "INSERT INTO tmp_course_selection_seed (student_no, course_name, preference_no, selected_at) VALUES\n"
            + ",\n".join(block)
            + ";"
        )

    first_choice_counts = Counter(course_name for _, course_name, preference, _ in preferences if preference == 1)
    second_choice_counts = Counter(course_name for _, course_name, preference, _ in preferences if preference == 2)

    sql = f"""-- 600 人选课测试数据（高一）
-- 使用顺序：
-- 1. 先导入本目录下的 classes.xlsx、students.xlsx、teachers.xlsx
-- 2. 确认这些学生已导入到目标学校，且学号前缀为 {STUDENT_PREFIX}
-- 3. 确认 teachers.xlsx 中的授课教师已经导入到目标学校
-- 4. 执行本 SQL，创建活动、课程、授课教师、按班名额和第一/第二志愿数据
--
-- 执行前请按实际学校修改 @school_id

SET NAMES utf8mb4;
SET @school_id = 2;
SET @grade_name = {sql_literal(GRADE_NAME)};
SET @event_name = {sql_literal(EVENT_NAME)};
SET @student_prefix = {sql_literal(STUDENT_PREFIX + "%")};

INSERT INTO selection_events (school_id, name, round1_start, round1_end, round2_start, round2_end, status, lottery_note, created_at)
SELECT
  @school_id,
  @event_name,
  '2026-04-01 08:00:00',
  '2026-04-08 18:00:00',
  '2026-04-09 08:00:00',
  '2026-04-10 18:00:00',
  'ROUND1',
  NULL,
  NOW()
FROM dual
WHERE NOT EXISTS (
  SELECT 1 FROM selection_events e WHERE e.school_id = @school_id AND e.name = @event_name
);

DROP TEMPORARY TABLE IF EXISTS tmp_course_seed;
CREATE TEMPORARY TABLE tmp_course_seed (
  course_name VARCHAR(100) NOT NULL,
  teacher_name VARCHAR(50) NOT NULL,
  capacity_mode VARCHAR(20) NOT NULL,
  total_capacity INT NOT NULL
);

INSERT INTO tmp_course_seed (course_name, teacher_name, capacity_mode, total_capacity) VALUES
{course_values};

DROP TEMPORARY TABLE IF EXISTS tmp_teacher_map;
CREATE TEMPORARY TABLE tmp_teacher_map
SELECT
  school_id,
  name AS teacher_name,
  MIN(id) AS teacher_id
FROM teachers
WHERE account_type = 'TEACHER' OR account_type IS NULL
GROUP BY school_id, name;

INSERT INTO courses (event_id, school_id, name, description, teacher_id, capacity_mode, total_capacity, current_count, status)
SELECT
  e.id,
  e.school_id,
  t.course_name,
  CONCAT('600 人抽签测试课程 - ', t.course_name),
  tm.teacher_id,
  t.capacity_mode,
  t.total_capacity,
  0,
  'ACTIVE'
FROM tmp_course_seed t
JOIN selection_events e
  ON e.school_id = @school_id AND e.name = @event_name
LEFT JOIN tmp_teacher_map tm
  ON tm.school_id = e.school_id AND tm.teacher_name = t.teacher_name
LEFT JOIN courses c
  ON c.event_id = e.id AND c.name = t.course_name
WHERE c.id IS NULL;

UPDATE courses c
JOIN selection_events e
  ON e.id = c.event_id
JOIN tmp_course_seed t
  ON t.course_name = c.name
LEFT JOIN tmp_teacher_map tm
  ON tm.school_id = e.school_id AND tm.teacher_name = t.teacher_name
SET c.description = CONCAT('600 人抽签测试课程 - ', t.course_name),
    c.teacher_id = tm.teacher_id,
    c.capacity_mode = t.capacity_mode,
    c.total_capacity = t.total_capacity,
    c.status = 'ACTIVE'
WHERE e.school_id = @school_id
  AND e.name = @event_name;

INSERT INTO event_students (event_id, student_id)
SELECT
  e.id,
  s.id
FROM selection_events e
JOIN students s
  ON s.school_id = e.school_id
LEFT JOIN event_students es
  ON es.event_id = e.id AND es.student_id = s.id
WHERE e.school_id = @school_id
  AND e.name = @event_name
  AND s.student_no LIKE @student_prefix
  AND es.id IS NULL;

INSERT INTO course_class_capacities (course_id, school_class_id, max_capacity, current_count)
SELECT
  c.id,
  sc.id,
  4,
  0
FROM courses c
JOIN selection_events e
  ON e.id = c.event_id
JOIN classes sc
  ON sc.school_id = e.school_id
JOIN grades g
  ON g.id = sc.grade_id
LEFT JOIN course_class_capacities cap
  ON cap.course_id = c.id AND cap.school_class_id = sc.id
WHERE e.school_id = @school_id
  AND e.name = @event_name
  AND c.name IN ({per_class_course_names})
  AND sc.type = '行政班'
  AND g.name = @grade_name
  AND sc.name IN ({class_names})
  AND cap.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_course_selection_seed;
CREATE TEMPORARY TABLE tmp_course_selection_seed (
  student_no VARCHAR(50) NOT NULL,
  course_name VARCHAR(100) NOT NULL,
  preference_no INT NOT NULL,
  selected_at DATETIME NOT NULL
);

{chr(10).join(selection_blocks)}

INSERT INTO course_selections (event_id, course_id, student_id, preference, round, status, selected_at, confirmed_at)
SELECT
  e.id,
  c.id,
  s.id,
  t.preference_no,
  1,
  'PENDING',
  t.selected_at,
  NULL
FROM tmp_course_selection_seed t
JOIN selection_events e
  ON e.school_id = @school_id AND e.name = @event_name
JOIN students s
  ON s.school_id = e.school_id AND s.student_no = t.student_no
JOIN courses c
  ON c.event_id = e.id AND c.name = t.course_name
LEFT JOIN course_selections cs
  ON cs.event_id = e.id
 AND cs.student_id = s.id
 AND cs.preference = t.preference_no
WHERE cs.id IS NULL;

DROP TEMPORARY TABLE IF EXISTS tmp_course_selection_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_course_seed;
DROP TEMPORARY TABLE IF EXISTS tmp_teacher_map;

-- 数据概要（供核对）
-- 第一志愿分布：
{chr(10).join(f'--   {name}: {first_choice_counts[name]} 人' for name in [course.name for course in COURSES])}
-- 第二志愿分布：
{chr(10).join(f'--   {name}: {second_choice_counts[name]} 人' for name in [course.name for course in COURSES])}
"""
    path.write_text(sql, encoding="utf-8")


def write_readme(path: Path, preferences: list[tuple[str, str, int, str]]) -> None:
    first_choice_counts = Counter(course_name for _, course_name, preference, _ in preferences if preference == 1)
    second_choice_counts = Counter(course_name for _, course_name, preference, _ in preferences if preference == 2)

    lines = [
        "# 600 人选课测试数据包",
        "",
        "## 内容",
        "",
        "- `classes.xlsx`：12 个行政班，`高一/1班` 到 `高一/12班`。",
        "- `students.xlsx`：600 名测试学生，每班 50 人，学号前缀统一为 `CS26`。",
        f"- `teachers.xlsx`：9 名测试授课教师，默认密码 `{DEFAULT_TEACHER_PASSWORD}`。",
        "- `courses.csv`：9 门测试课程、授课教师与名额设置说明。",
        f"- `course-selection-600.sql`：创建活动 `{EVENT_NAME}`、9 门课程、授课教师、按班名额、活动参与名单，以及 600 名学生的第一/第二志愿。",
        "",
        "## 课程设置",
        "",
    ]

    for course in COURSES:
        if course.capacity_mode == "GLOBAL":
            lines.append(f"- {course.name}：授课教师 `{course.teacher_name}`，全局名额 {course.total_capacity}。")
        else:
            lines.append(
                f"- {course.name}：授课教师 `{course.teacher_name}`，按班级名额，每个行政班 {course.per_class_capacity} 人，总名额 {course.total_capacity}。"
            )

    lines.extend([
        "",
        "## 导入顺序",
        "",
        "1. 在系统里先导入 `classes.xlsx`。",
        "2. 再导入 `students.xlsx`。",
        "3. 再导入 `teachers.xlsx`。",
        "4. 修改 `course-selection-600.sql` 里的 `@school_id` 为目标学校 ID。",
        "5. 执行 `course-selection-600.sql`。",
        "6. 管理端进入该活动后，可以直接测试第一轮结算。",
        "",
        "## 说明",
        "",
        "- 这套数据的目标是制造抽签压力：600 名学生参与，9 门课程总名额小于 600。",
        "- SQL 会按课程预设的教师姓名去匹配本校教师，并写入 `courses.teacher_id`。",
        "- 两门按班名额课程分别为 `田径提升`、`跳绳挑战`，每个行政班 4 个名额。",
        "- 第一志愿和第二志愿都已预生成，状态为 `PENDING`，可直接用于测试新抽签规则。",
        "",
        "## 志愿分布",
        "",
        "### 第一志愿",
    ])

    for course in COURSES:
        lines.append(f"- {course.name}：{first_choice_counts[course.name]} 人")

    lines.extend([
        "",
        "### 第二志愿",
    ])

    for course in COURSES:
        lines.append(f"- {course.name}：{second_choice_counts[course.name]} 人")

    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    admin_classes = build_admin_classes()
    students = build_students()
    teachers = build_teachers()
    preferences = build_preferences(students)

    write_xlsx(
        OUTPUT_DIR / "classes.xlsx",
        "班级",
        ["班级名称", "类型", "年级"],
        admin_classes,
    )
    write_xlsx(
        OUTPUT_DIR / "students.xlsx",
        "学生",
        ["年级", "班级", "姓名", "性别", "学号", "身份证号", "选修课", "学籍状态"],
        students,
    )
    write_xlsx(
        OUTPUT_DIR / "teachers.xlsx",
        "教师",
        ["姓名", "手机号", "密码", "行政班", "选修课", "账号类型", "组织管理员类型"],
        teachers,
    )
    write_courses_csv(OUTPUT_DIR / "courses.csv")
    write_sql(OUTPUT_DIR / "course-selection-600.sql", admin_classes, preferences)
    write_readme(OUTPUT_DIR / "README.md", preferences)

    print(f"Generated test data under: {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
