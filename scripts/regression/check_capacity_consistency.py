import argparse
import csv
import json
import os
import re
import shutil
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional


APP_CONFIG_FILES = [
    Path("src/main/resources/application.yml"),
    Path("src/main/resources/application-local.yml"),
]


@dataclass
class DbConfig:
    host: str
    port: int
    database: str
    username: str
    password: str
    mysql_bin: str


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="只读校验课程容量统计是否与 CONFIRMED 实际数据一致。"
    )
    scope = parser.add_mutually_exclusive_group()
    scope.add_argument("--event-id", type=int, help="仅校验指定活动 ID")
    scope.add_argument("--all-events", action="store_true", help="校验全部活动")
    parser.add_argument("--host", help="MySQL 主机，默认从 application*.yml 推断")
    parser.add_argument("--port", type=int, help="MySQL 端口，默认从 application*.yml 推断")
    parser.add_argument("--database", help="数据库名，默认从 application*.yml 推断")
    parser.add_argument("--username", help="数据库用户名，默认从 application*.yml 推断")
    parser.add_argument("--password", help="数据库密码，默认从 application*.yml 推断")
    parser.add_argument("--mysql-bin", help="mysql 客户端路径，默认自动查找")
    parser.add_argument(
        "--results-dir",
        default="scripts/regression/results",
        help="结果输出根目录，默认 scripts/regression/results",
    )
    return parser


def resolve_placeholder(value: str) -> str:
    value = value.strip().strip('"').strip("'")
    match = re.fullmatch(r"\$\{([^:}]+):(.*)\}", value)
    if not match:
        return value
    env_name = match.group(1)
    default_value = match.group(2)
    return os.environ.get(env_name, default_value)


def load_datasource_defaults() -> Dict[str, str]:
    values: Dict[str, str] = {}
    for path in APP_CONFIG_FILES:
        if not path.exists():
            continue
        text = path.read_text(encoding="utf-8")
        for key in ("url", "username", "password"):
            match = re.search(rf"^\s*{key}:\s*(.+?)\s*$", text, re.MULTILINE)
            if match:
                values[key] = resolve_placeholder(match.group(1))
    return values


def parse_jdbc_url(jdbc_url: str) -> Dict[str, object]:
    match = re.match(r"jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)", jdbc_url)
    if not match:
        raise RuntimeError(f"无法解析 JDBC 地址：{jdbc_url}")
    return {
        "host": match.group(1),
        "port": int(match.group(2) or "3306"),
        "database": match.group(3),
    }


def load_db_config(args: argparse.Namespace) -> DbConfig:
    defaults = load_datasource_defaults()
    jdbc_parts = parse_jdbc_url(defaults.get("url", "jdbc:mysql://127.0.0.1:3306/pe_assistant"))
    mysql_bin = args.mysql_bin or shutil.which("mysql")
    if not mysql_bin:
        raise RuntimeError("未找到 mysql 客户端，请通过 --mysql-bin 指定路径")
    return DbConfig(
        host=args.host or str(jdbc_parts["host"]),
        port=args.port or int(jdbc_parts["port"]),
        database=args.database or str(jdbc_parts["database"]),
        username=args.username or defaults.get("username", "root"),
        password=args.password or defaults.get("password", ""),
        mysql_bin=mysql_bin,
    )


def run_mysql_query(config: DbConfig, sql: str) -> List[List[str]]:
    env = os.environ.copy()
    if config.password:
        env["MYSQL_PWD"] = config.password
    command = [
        config.mysql_bin,
        f"--host={config.host}",
        f"--port={config.port}",
        f"--user={config.username}",
        f"--database={config.database}",
        "--default-character-set=utf8mb4",
        "--batch",
        "--raw",
        "--skip-column-names",
        "-e",
        sql,
    ]
    completed = subprocess.run(
        command,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or "mysql 查询执行失败")
    rows = []
    for line in completed.stdout.splitlines():
        if not line.strip():
            continue
        rows.append(line.split("\t"))
    return rows


def scalar_query(config: DbConfig, sql: str) -> Optional[str]:
    rows = run_mysql_query(config, sql)
    return rows[0][0] if rows and rows[0] else None


def build_scope_clause(event_id: Optional[int]) -> str:
    if event_id is None:
        return ""
    return f" AND c.event_id = {event_id} "


def as_int(value: object) -> int:
    if value is None or value == "":
        return 0
    return int(str(value))


def write_csv(path: Path, rows: List[Dict[str, object]], headers: List[str]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers)
        writer.writeheader()
        for row in rows:
            writer.writerow({header: row.get(header, "") for header in headers})


def output_dir(results_root: Path, event_id: Optional[int]) -> Path:
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    suffix = f"_event_{event_id}" if event_id is not None else "_all_events"
    path = results_root / f"capacity_consistency_{timestamp}{suffix}"
    path.mkdir(parents=True, exist_ok=True)
    return path


def latest_event_id(config: DbConfig) -> Optional[int]:
    value = scalar_query(config, "SELECT MAX(id) FROM selection_events;")
    if value in (None, "NULL", ""):
        return None
    return int(value)


def fetch_course_rows(config: DbConfig, event_id: Optional[int]) -> List[Dict[str, object]]:
    scope = build_scope_clause(event_id)
    sql = f"""
SELECT
  e.id,
  e.name,
  c.id,
  c.name,
  c.capacity_mode,
  c.status,
  c.total_capacity,
  c.current_count,
  COALESCE(sel.actual_unique_confirmed, 0),
  COALESCE(sel.actual_confirmed_rows, 0),
  COALESCE(cap.sum_current_count, 0),
  COALESCE(cap.sum_max_capacity, 0),
  COALESCE(cap.capacity_row_count, 0)
FROM courses c
JOIN selection_events e ON e.id = c.event_id
LEFT JOIN (
  SELECT
    course_id,
    COUNT(DISTINCT student_id) AS actual_unique_confirmed,
    COUNT(*) AS actual_confirmed_rows
  FROM course_selections
  WHERE status = 'CONFIRMED'
  GROUP BY course_id
) sel ON sel.course_id = c.id
LEFT JOIN (
  SELECT
    course_id,
    SUM(current_count) AS sum_current_count,
    SUM(max_capacity) AS sum_max_capacity,
    COUNT(*) AS capacity_row_count
  FROM course_class_capacities
  GROUP BY course_id
) cap ON cap.course_id = c.id
WHERE 1 = 1 {scope}
ORDER BY e.id DESC, c.id ASC;
"""
    rows = []
    for item in run_mysql_query(config, sql):
        row = {
            "eventId": as_int(item[0]),
            "eventName": item[1],
            "courseId": as_int(item[2]),
            "courseName": item[3],
            "capacityMode": item[4],
            "courseStatus": item[5],
            "totalCapacity": as_int(item[6]),
            "storedCourseCurrent": as_int(item[7]),
            "actualCourseConfirmed": as_int(item[8]),
            "actualConfirmedRows": as_int(item[9]),
            "storedClassCurrentSum": as_int(item[10]),
            "classCapacitySum": as_int(item[11]),
            "classCapacityRowCount": as_int(item[12]),
        }
        row["courseCurrentMismatch"] = row["storedCourseCurrent"] != row["actualCourseConfirmed"]
        row["storedExceedsTotalCapacity"] = row["storedCourseCurrent"] > row["totalCapacity"]
        row["actualExceedsTotalCapacity"] = row["actualCourseConfirmed"] > row["totalCapacity"]
        row["perClassStoredSumMismatch"] = (
            row["capacityMode"] == "PER_CLASS"
            and row["storedCourseCurrent"] != row["storedClassCurrentSum"]
        )
        row["perClassCapacitySumMismatch"] = (
            row["capacityMode"] == "PER_CLASS"
            and row["totalCapacity"] != row["classCapacitySum"]
        )
        row["hasMismatch"] = any(
            [
                row["courseCurrentMismatch"],
                row["storedExceedsTotalCapacity"],
                row["actualExceedsTotalCapacity"],
                row["perClassStoredSumMismatch"],
                row["perClassCapacitySumMismatch"],
            ]
        )
        rows.append(row)
    return rows


def fetch_per_class_rows(config: DbConfig, event_id: Optional[int]) -> List[Dict[str, object]]:
    scope = build_scope_clause(event_id)
    sql = f"""
SELECT
  e.id,
  e.name,
  c.id,
  c.name,
  sc.id,
  sc.name,
  ccc.max_capacity,
  ccc.current_count,
  COALESCE(act.actual_class_confirmed, 0),
  COALESCE(act.actual_confirmed_rows, 0)
FROM course_class_capacities ccc
JOIN courses c ON c.id = ccc.course_id
JOIN selection_events e ON e.id = c.event_id
JOIN classes sc ON sc.id = ccc.school_class_id
LEFT JOIN (
  SELECT
    cs.course_id,
    s.class_id,
    COUNT(DISTINCT cs.student_id) AS actual_class_confirmed,
    COUNT(*) AS actual_confirmed_rows
  FROM course_selections cs
  JOIN students s ON s.id = cs.student_id
  WHERE cs.status = 'CONFIRMED'
  GROUP BY cs.course_id, s.class_id
) act ON act.course_id = ccc.course_id AND act.class_id = ccc.school_class_id
WHERE c.capacity_mode = 'PER_CLASS' {scope}
ORDER BY e.id DESC, c.id ASC, sc.id ASC;
"""
    rows = []
    for item in run_mysql_query(config, sql):
        row = {
            "eventId": as_int(item[0]),
            "eventName": item[1],
            "courseId": as_int(item[2]),
            "courseName": item[3],
            "classId": as_int(item[4]),
            "className": item[5],
            "maxCapacity": as_int(item[6]),
            "storedClassCurrent": as_int(item[7]),
            "actualClassConfirmed": as_int(item[8]),
            "actualConfirmedRows": as_int(item[9]),
        }
        row["classCurrentMismatch"] = row["storedClassCurrent"] != row["actualClassConfirmed"]
        row["storedExceedsMaxCapacity"] = row["storedClassCurrent"] > row["maxCapacity"]
        row["actualExceedsMaxCapacity"] = row["actualClassConfirmed"] > row["maxCapacity"]
        row["hasMismatch"] = any(
            [
                row["classCurrentMismatch"],
                row["storedExceedsMaxCapacity"],
                row["actualExceedsMaxCapacity"],
            ]
        )
        rows.append(row)
    return rows


def fetch_missing_capacity_rows(config: DbConfig, event_id: Optional[int]) -> List[Dict[str, object]]:
    scope = build_scope_clause(event_id)
    sql = f"""
SELECT
  e.id,
  e.name,
  c.id,
  c.name,
  s.class_id,
  COALESCE(sc.name, CONCAT('ID=', s.class_id)),
  COUNT(DISTINCT cs.student_id),
  COUNT(*)
FROM course_selections cs
JOIN courses c ON c.id = cs.course_id
JOIN selection_events e ON e.id = c.event_id
JOIN students s ON s.id = cs.student_id
LEFT JOIN classes sc ON sc.id = s.class_id
LEFT JOIN course_class_capacities ccc
  ON ccc.course_id = cs.course_id AND ccc.school_class_id = s.class_id
WHERE cs.status = 'CONFIRMED'
  AND c.capacity_mode = 'PER_CLASS'
  AND ccc.id IS NULL
  {scope}
GROUP BY e.id, e.name, c.id, c.name, s.class_id, sc.name
ORDER BY e.id DESC, c.id ASC, s.class_id ASC;
"""
    rows = []
    for item in run_mysql_query(config, sql):
        rows.append(
            {
                "eventId": as_int(item[0]),
                "eventName": item[1],
                "courseId": as_int(item[2]),
                "courseName": item[3],
                "classId": as_int(item[4]),
                "className": item[5],
                "actualClassConfirmed": as_int(item[6]),
                "actualConfirmedRows": as_int(item[7]),
            }
        )
    return rows


def fetch_duplicate_confirmed_rows(config: DbConfig, event_id: Optional[int]) -> List[Dict[str, object]]:
    scope = f" AND cs.event_id = {event_id} " if event_id is not None else ""
    sql = f"""
SELECT
  e.id,
  e.name,
  c.id,
  c.name,
  cs.student_id,
  s.student_no,
  s.name,
  COUNT(*)
FROM course_selections cs
JOIN courses c ON c.id = cs.course_id
JOIN selection_events e ON e.id = cs.event_id
JOIN students s ON s.id = cs.student_id
WHERE cs.status = 'CONFIRMED'
  {scope}
GROUP BY e.id, e.name, c.id, c.name, cs.student_id, s.student_no, s.name
HAVING COUNT(*) > 1
ORDER BY e.id DESC, c.id ASC, cs.student_id ASC;
"""
    rows = []
    for item in run_mysql_query(config, sql):
        rows.append(
            {
                "eventId": as_int(item[0]),
                "eventName": item[1],
                "courseId": as_int(item[2]),
                "courseName": item[3],
                "studentId": as_int(item[4]),
                "studentNo": item[5],
                "studentName": item[6],
                "duplicateConfirmedRows": as_int(item[7]),
            }
        )
    return rows


def main() -> None:
    args = build_parser().parse_args()
    db_config = load_db_config(args)
    selected_event_id = None if args.all_events else (args.event_id or latest_event_id(db_config))
    if not args.all_events and selected_event_id is None:
        raise SystemExit("未找到可校验的选课活动")

    results_root = Path(args.results_dir)
    run_dir = output_dir(results_root, selected_event_id)

    course_rows = fetch_course_rows(db_config, selected_event_id)
    per_class_rows = fetch_per_class_rows(db_config, selected_event_id)
    missing_capacity_rows = fetch_missing_capacity_rows(db_config, selected_event_id)
    duplicate_confirmed_rows = fetch_duplicate_confirmed_rows(db_config, selected_event_id)

    course_mismatch_rows = [row for row in course_rows if row["hasMismatch"]]
    per_class_mismatch_rows = [row for row in per_class_rows if row["hasMismatch"]]

    write_csv(
        run_dir / "course_capacity_check.csv",
        course_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "capacityMode",
            "courseStatus",
            "totalCapacity",
            "storedCourseCurrent",
            "actualCourseConfirmed",
            "actualConfirmedRows",
            "storedClassCurrentSum",
            "classCapacitySum",
            "classCapacityRowCount",
            "courseCurrentMismatch",
            "storedExceedsTotalCapacity",
            "actualExceedsTotalCapacity",
            "perClassStoredSumMismatch",
            "perClassCapacitySumMismatch",
            "hasMismatch",
        ],
    )
    write_csv(
        run_dir / "course_capacity_mismatches.csv",
        course_mismatch_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "capacityMode",
            "courseStatus",
            "totalCapacity",
            "storedCourseCurrent",
            "actualCourseConfirmed",
            "actualConfirmedRows",
            "storedClassCurrentSum",
            "classCapacitySum",
            "classCapacityRowCount",
            "courseCurrentMismatch",
            "storedExceedsTotalCapacity",
            "actualExceedsTotalCapacity",
            "perClassStoredSumMismatch",
            "perClassCapacitySumMismatch",
            "hasMismatch",
        ],
    )
    write_csv(
        run_dir / "per_class_capacity_check.csv",
        per_class_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "classId",
            "className",
            "maxCapacity",
            "storedClassCurrent",
            "actualClassConfirmed",
            "actualConfirmedRows",
            "classCurrentMismatch",
            "storedExceedsMaxCapacity",
            "actualExceedsMaxCapacity",
            "hasMismatch",
        ],
    )
    write_csv(
        run_dir / "per_class_capacity_mismatches.csv",
        per_class_mismatch_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "classId",
            "className",
            "maxCapacity",
            "storedClassCurrent",
            "actualClassConfirmed",
            "actualConfirmedRows",
            "classCurrentMismatch",
            "storedExceedsMaxCapacity",
            "actualExceedsMaxCapacity",
            "hasMismatch",
        ],
    )
    write_csv(
        run_dir / "per_class_missing_capacity.csv",
        missing_capacity_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "classId",
            "className",
            "actualClassConfirmed",
            "actualConfirmedRows",
        ],
    )
    write_csv(
        run_dir / "duplicate_confirmed_selection_details.csv",
        duplicate_confirmed_rows,
        [
            "eventId",
            "eventName",
            "courseId",
            "courseName",
            "studentId",
            "studentNo",
            "studentName",
            "duplicateConfirmedRows",
        ],
    )

    summary = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "scope": {
            "allEvents": args.all_events,
            "eventId": selected_event_id,
        },
        "database": {
            "host": db_config.host,
            "port": db_config.port,
            "database": db_config.database,
            "username": db_config.username,
        },
        "overallConsistent": not any(
            [
                course_mismatch_rows,
                per_class_mismatch_rows,
                missing_capacity_rows,
                duplicate_confirmed_rows,
            ]
        ),
        "counts": {
            "courseRows": len(course_rows),
            "courseMismatchRows": len(course_mismatch_rows),
            "perClassRows": len(per_class_rows),
            "perClassMismatchRows": len(per_class_mismatch_rows),
            "missingCapacityRows": len(missing_capacity_rows),
            "duplicateConfirmedSelectionRows": len(duplicate_confirmed_rows),
        },
        "files": {
            "courseCapacityCheckCsv": str(run_dir / "course_capacity_check.csv"),
            "courseCapacityMismatchesCsv": str(run_dir / "course_capacity_mismatches.csv"),
            "perClassCapacityCheckCsv": str(run_dir / "per_class_capacity_check.csv"),
            "perClassCapacityMismatchesCsv": str(run_dir / "per_class_capacity_mismatches.csv"),
            "perClassMissingCapacityCsv": str(run_dir / "per_class_missing_capacity.csv"),
            "duplicateConfirmedSelectionDetailsCsv": str(run_dir / "duplicate_confirmed_selection_details.csv"),
            "summaryJson": str(run_dir / "summary.json"),
        },
    }
    (run_dir / "summary.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("容量一致性校验完成：")
    print(f"- 输出目录：{run_dir}")
    print(f"- 课程级差异：{len(course_mismatch_rows)}")
    print(f"- 班级级差异：{len(per_class_mismatch_rows)}")
    print(f"- 缺失容量配置：{len(missing_capacity_rows)}")
    print(f"- 重复 CONFIRMED 记录：{len(duplicate_confirmed_rows)}")


if __name__ == "__main__":
    main()
