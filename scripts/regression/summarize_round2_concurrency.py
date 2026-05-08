import argparse
import csv
import json
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Dict, Iterable, List, Optional


PER_RUN_SUMMARY_NAME = "round2_concurrency_summary.json"
AGGREGATE_CSV_NAME = "round2_regression_summary.csv"
AGGREGATE_JSON_NAME = "round2_regression_summary.json"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="汇总 round2_concurrency_results.csv，生成固定格式的第二轮回归摘要文件。"
    )
    parser.add_argument(
        "--results-root",
        default="scripts/regression/results",
        help="回归结果根目录，默认 scripts/regression/results",
    )
    parser.add_argument(
        "--run-dir",
        action="append",
        default=[],
        help="仅汇总指定运行目录，可重复传入；不传则扫描 results-root 下全部运行目录",
    )
    parser.add_argument(
        "--include-existing-summary",
        action="store_true",
        help="将已有 summary.json 中的活动与容量信息一并写入固定摘要",
    )
    return parser


def read_csv_rows(path: Path) -> List[Dict[str, str]]:
    with path.open("r", encoding="utf-8-sig", newline="") as handle:
        return list(csv.DictReader(handle))


def read_json(path: Path) -> Optional[dict]:
    if not path.exists():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def safe_int(value: object, default: int = 0) -> int:
    try:
        return int(str(value).strip())
    except (TypeError, ValueError):
        return default


def safe_float(value: object, default: float = 0.0) -> float:
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return default


def classify_code(code: int) -> str:
    if code == 200:
        return "success"
    if 400 <= code < 500:
        return "business_reject"
    if code <= 0 or code >= 500:
        return "exception"
    return "other_failure"


def summarize_messages(rows: Iterable[Dict[str, str]]) -> List[Dict[str, object]]:
    counter = Counter()
    for row in rows:
        message = (row.get("message") or "").strip() or "<empty>"
        counter[message] += 1
    return [
        {"message": message, "count": count}
        for message, count in counter.most_common()
    ]


def summarize_codes(rows: Iterable[Dict[str, str]]) -> List[Dict[str, object]]:
    counter = Counter()
    for row in rows:
        counter[str(safe_int(row.get("code"), -9999))] += 1
    return [
        {"code": code, "count": count}
        for code, count in sorted(counter.items(), key=lambda item: (safe_int(item[0]), item[0]))
    ]


def resolve_runs(results_root: Path, run_dirs: List[str]) -> List[Path]:
    if run_dirs:
        runs = [Path(run_dir) for run_dir in run_dirs]
    else:
        runs = [path.parent for path in results_root.rglob("round2_concurrency_results.csv")]
    resolved = []
    seen = set()
    for run in runs:
        run_path = run if run.is_absolute() else (results_root / run if not run.exists() else run)
        csv_path = run_path / "round2_concurrency_results.csv"
        if csv_path.exists() and run_path not in seen:
            seen.add(run_path)
            resolved.append(run_path)
    return sorted(resolved)


def compute_oversold(second_round: dict, success_count: int) -> (Optional[bool], Optional[int], Optional[int]):
    target_capacity = second_round.get("targetCourseCapacity")
    final_confirmed = second_round.get("finalConfirmedInTargetCourse")
    oversold = second_round.get("oversold")
    oversold_by = second_round.get("oversoldBy")

    if isinstance(oversold, bool):
        return oversold, safe_int(oversold_by, 0), safe_int(final_confirmed, 0) if final_confirmed is not None else None

    if target_capacity is None:
        return None, None, safe_int(final_confirmed, 0) if final_confirmed is not None else None

    capacity = safe_int(target_capacity, 0)
    if final_confirmed is not None:
        final_value = safe_int(final_confirmed, 0)
        computed = final_value > capacity
        return computed, max(0, final_value - capacity), final_value

    computed = success_count > capacity
    return computed, max(0, success_count - capacity), None


def relative_to(path: Path, base: Path) -> str:
    try:
        return str(path.relative_to(base))
    except ValueError:
        return str(path)


def build_run_summary(run_dir: Path, results_root: Path, include_existing_summary: bool) -> Dict[str, object]:
    csv_path = run_dir / "round2_concurrency_results.csv"
    summary_path = run_dir / "summary.json"

    rows = read_csv_rows(csv_path)
    existing_summary = read_json(summary_path) if include_existing_summary or summary_path.exists() else None
    second_round = (existing_summary or {}).get("secondRound", {})

    success_rows = []
    business_rows = []
    exception_rows = []
    other_failure_rows = []
    latencies = []

    for row in rows:
        code = safe_int(row.get("code"), -9999)
        kind = classify_code(code)
        latencies.append(safe_float(row.get("elapsedMs"), 0.0))
        if kind == "success":
            success_rows.append(row)
        elif kind == "business_reject":
            business_rows.append(row)
        elif kind == "exception":
            exception_rows.append(row)
        else:
            other_failure_rows.append(row)

    oversold, oversold_by, final_confirmed = compute_oversold(second_round, len(success_rows))
    total_requests = len(rows)
    summary = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "runName": run_dir.name,
        "runDir": relative_to(run_dir, results_root),
        "sourceCsv": relative_to(csv_path, results_root),
        "eventId": (existing_summary or {}).get("eventId"),
        "eventName": (existing_summary or {}).get("eventName"),
        "targetCourse": second_round.get("targetCourse"),
        "targetCourseId": second_round.get("targetCourseId"),
        "targetCourseCapacity": second_round.get("targetCourseCapacity"),
        "concurrentUsers": second_round.get("concurrentUsers", total_requests),
        "totalRequests": total_requests,
        "successCount": len(success_rows),
        "businessRejectCount": len(business_rows),
        "exceptionCount": len(exception_rows),
        "otherFailureCount": len(other_failure_rows),
        "successRate": round((len(success_rows) / total_requests) * 100, 2) if total_requests else 0.0,
        "oversold": oversold,
        "oversoldBy": oversold_by,
        "finalConfirmedInTargetCourse": final_confirmed,
        "codeDistribution": summarize_codes(rows),
        "businessRejectReasons": summarize_messages(business_rows),
        "exceptionReasons": summarize_messages(exception_rows),
        "otherFailureReasons": summarize_messages(other_failure_rows),
        "latencyMs": {
            "min": round(min(latencies), 2) if latencies else 0.0,
            "avg": round(sum(latencies) / len(latencies), 2) if latencies else 0.0,
            "max": round(max(latencies), 2) if latencies else 0.0,
        },
    }
    if include_existing_summary:
        summary["summarySource"] = relative_to(summary_path, results_root) if summary_path.exists() else None
    return summary


def write_csv(path: Path, rows: List[Dict[str, object]], headers: List[str]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=headers)
        writer.writeheader()
        for row in rows:
            writer.writerow({header: row.get(header, "") for header in headers})


def main() -> None:
    args = build_parser().parse_args()
    results_root = Path(args.results_root)
    run_dirs = resolve_runs(results_root, args.run_dir)
    if not run_dirs:
        raise SystemExit(f"未找到可汇总的 round2_concurrency_results.csv：{results_root}")

    aggregate_rows = []
    aggregate_json = {
        "schemaVersion": 1,
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "resultsRoot": str(results_root),
        "runs": [],
    }

    for run_dir in run_dirs:
        run_summary = build_run_summary(run_dir, results_root, args.include_existing_summary)
        per_run_path = run_dir / PER_RUN_SUMMARY_NAME
        per_run_path.write_text(json.dumps(run_summary, ensure_ascii=False, indent=2), encoding="utf-8")

        aggregate_rows.append(
            {
                "runName": run_summary["runName"],
                "runDir": run_summary["runDir"],
                "eventId": run_summary["eventId"],
                "eventName": run_summary["eventName"],
                "targetCourse": run_summary["targetCourse"],
                "targetCourseCapacity": run_summary["targetCourseCapacity"],
                "totalRequests": run_summary["totalRequests"],
                "successCount": run_summary["successCount"],
                "businessRejectCount": run_summary["businessRejectCount"],
                "exceptionCount": run_summary["exceptionCount"],
                "otherFailureCount": run_summary["otherFailureCount"],
                "successRate": run_summary["successRate"],
                "oversold": run_summary["oversold"],
                "oversoldBy": run_summary["oversoldBy"],
                "finalConfirmedInTargetCourse": run_summary["finalConfirmedInTargetCourse"],
                "sourceCsv": run_summary["sourceCsv"],
                "perRunSummary": relative_to(per_run_path, results_root),
            }
        )
        aggregate_json["runs"].append(run_summary)

    write_csv(
        results_root / AGGREGATE_CSV_NAME,
        aggregate_rows,
        [
            "runName",
            "runDir",
            "eventId",
            "eventName",
            "targetCourse",
            "targetCourseCapacity",
            "totalRequests",
            "successCount",
            "businessRejectCount",
            "exceptionCount",
            "otherFailureCount",
            "successRate",
            "oversold",
            "oversoldBy",
            "finalConfirmedInTargetCourse",
            "sourceCsv",
            "perRunSummary",
        ],
    )
    (results_root / AGGREGATE_JSON_NAME).write_text(
        json.dumps(aggregate_json, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"已生成 {len(run_dirs)} 份第二轮汇总摘要。")
    print(f"- {results_root / AGGREGATE_CSV_NAME}")
    print(f"- {results_root / AGGREGATE_JSON_NAME}")


if __name__ == "__main__":
    main()
