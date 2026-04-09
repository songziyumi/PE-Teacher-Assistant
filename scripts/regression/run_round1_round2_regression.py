import argparse
import json
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

from course_selection_regression_lib import (
    DEFAULT_COURSE_SPECS,
    RegressionConfig,
    activate_and_confirm_round1,
    activate_all_courses,
    api_login_student,
    create_courses,
    create_event,
    env_or_required,
    fetch_course_map_for_current_event,
    fetch_round2_token,
    fetch_student_selection_result,
    latency_summary,
    prepare_standard_run,
    process_round1,
    save_event_students,
    second_round_select_with_token,
    start_round1,
    write_csv,
    write_summary,
)


ROUND1_PREF1 = "篮球"
ROUND1_PREF2 = "乒乓球"
ROUND2_TARGET = "足球"


def build_parser() -> argparse.ArgumentParser:
    base_url, base_url_required = env_or_required("COURSE_SELECTION_BASE_URL")
    admin_username, admin_username_required = env_or_required("COURSE_SELECTION_ADMIN_USERNAME")
    admin_password, admin_password_required = env_or_required("COURSE_SELECTION_ADMIN_PASSWORD")

    parser = argparse.ArgumentParser(description="执行第一轮抽签 + 第二轮并发抢课全链路回归。")
    parser.add_argument("--base-url", default=base_url, required=base_url_required)
    parser.add_argument("--admin-username", default=admin_username, required=admin_username_required)
    parser.add_argument("--admin-password", default=admin_password, required=admin_password_required)
    parser.add_argument("--accounts-csv", default="scripts/jmeter/data/student_accounts.csv")
    parser.add_argument("--results-dir", default="scripts/regression/results")
    parser.add_argument("--grade-name", default="高一")
    parser.add_argument("--class-count", type=int, default=10)
    parser.add_argument("--round1-pref1", default=ROUND1_PREF1)
    parser.add_argument("--round1-pref2", default=ROUND1_PREF2)
    parser.add_argument("--round2-target", default=ROUND2_TARGET)
    parser.add_argument("--round2-max-users", type=int, default=200)
    parser.add_argument("--preference-workers", type=int, default=40)
    parser.add_argument("--confirm-workers", type=int, default=5)
    parser.add_argument("--result-workers", type=int, default=50)
    return parser


def submit_preferences(account, course_map, pref1_name, pref2_name, base_url):
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    pref1_id = course_map[pref1_name]
    pref2_id = course_map[pref2_name]
    result1 = client.api_request("POST", f"/api/student/courses/{pref1_id}/prefer?preference=1", token=token)
    result2 = client.api_request("POST", f"/api/student/courses/{pref2_id}/prefer?preference=2", token=token)
    return {
        "username": account.username,
        "pref1Code": str(result1.get("code")),
        "pref1Message": str(result1.get("message")),
        "pref2Code": str(result2.get("code")),
        "pref2Message": str(result2.get("message")),
    }


def main() -> None:
    args = build_parser().parse_args()
    config = RegressionConfig(
        base_url=args.base_url,
        admin_username=args.admin_username,
        admin_password=args.admin_password,
        accounts_csv=Path(args.accounts_csv),
        results_dir=Path(args.results_dir),
        grade_name=args.grade_name,
        class_count=args.class_count,
    )

    prepared = prepare_standard_run(config, "round1_round2")
    event_name = f"{prepared.started.strftime('%Y-%m-%d %H:%M:%S')} 第一轮抽签+第二轮并发回归 {prepared.prefix}"
    event_id = create_event(prepared.admin_client, event_name, prepared.admin_csrf)

    save_event_students(prepared.admin_client, event_id, prepared.student_ids, prepared.admin_csrf)
    create_courses(prepared.admin_client, event_id, prepared.classes, prepared.admin_csrf, DEFAULT_COURSE_SPECS)
    activate_all_courses(prepared.admin_client, event_id, prepared.admin_csrf)
    start_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    sample_course_map = fetch_course_map_for_current_event(prepared.accounts[0], config.base_url)

    pref_submit_rows = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(prepared.accounts), args.preference_workers))) as executor:
        futures = [
            executor.submit(
                submit_preferences,
                account,
                sample_course_map,
                args.round1_pref1,
                args.round1_pref2,
                config.base_url,
            )
            for account in prepared.accounts
        ]
        for future in as_completed(futures):
            pref_submit_rows.append(future.result())
    write_csv(
        prepared.run_dir / "round1_preference_submit.csv",
        pref_submit_rows,
        ["username", "pref1Code", "pref1Message", "pref2Code", "pref2Message"],
    )

    confirm_rows = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(prepared.accounts), args.confirm_workers))) as executor:
        futures = [executor.submit(activate_and_confirm_round1, account, config.base_url) for account in prepared.accounts]
        for future in as_completed(futures):
            confirm_rows.append(future.result())
    write_csv(
        prepared.run_dir / "round1_confirm_submit.csv",
        confirm_rows,
        ["username", "student_no", "student_name", "new_password", "status"],
    )

    lottery_duration_ms = process_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    selection_results = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(prepared.accounts), args.result_workers))) as executor:
        futures = [executor.submit(fetch_student_selection_result, account, config.base_url) for account in prepared.accounts]
        for future in as_completed(futures):
            selection_results.append(future.result())

    first_choice_confirmed = 0
    second_choice_confirmed = 0
    unsuccessful_accounts = []
    round1_course_counts = {}
    per_student_rows = []
    for item in selection_results:
        account = item["account"]
        confirmed = item["confirmed"]
        selections = item["selections"]
        if confirmed:
            preference = int(confirmed.get("preference", 0))
            course_name = str(confirmed.get("courseName"))
            round1_course_counts[course_name] = round1_course_counts.get(course_name, 0) + 1
            if preference == 1:
                first_choice_confirmed += 1
            elif preference == 2:
                second_choice_confirmed += 1
            status = "CONFIRMED"
        else:
            unsuccessful_accounts.append(account)
            status = "UNSUCCESSFUL"
        per_student_rows.append(
            {
                "username": account.username,
                "student_no": account.student_no,
                "student_name": account.student_name,
                "status": status,
                "confirmed_course": confirmed.get("courseName") if confirmed else "",
                "confirmed_preference": confirmed.get("preference") if confirmed else "",
                "selection_json": json.dumps(selections, ensure_ascii=False),
            }
        )

    write_csv(
        prepared.run_dir / "round1_student_results.csv",
        per_student_rows,
        ["username", "student_no", "student_name", "status", "confirmed_course", "confirmed_preference", "selection_json"],
    )

    round2_users = min(args.round2_max_users, len(unsuccessful_accounts))
    round2_accounts = unsuccessful_accounts[:round2_users]
    round2_course_id = sample_course_map[args.round2_target]

    round2_tokens = []
    if round2_accounts:
        with ThreadPoolExecutor(max_workers=max(1, min(len(round2_accounts), args.result_workers))) as executor:
            futures = [executor.submit(fetch_round2_token, account, config.base_url) for account in round2_accounts]
            for future in as_completed(futures):
                round2_tokens.append(future.result())

    start_event = threading.Event()
    round2_started_at = time.perf_counter()
    round2_rows = []
    if round2_tokens:
        with ThreadPoolExecutor(max_workers=max(1, len(round2_tokens))) as executor:
            futures = [
                executor.submit(
                    second_round_select_with_token,
                    item["account"],
                    item["token"],
                    round2_course_id,
                    start_event,
                    config.base_url,
                )
                for item in round2_tokens
            ]
            start_event.set()
            for future in as_completed(futures):
                round2_rows.append(future.result())
    round2_wall_ms = (time.perf_counter() - round2_started_at) * 1000

    write_csv(
        prepared.run_dir / "round2_concurrency_results.csv",
        round2_rows,
        ["username", "student_no", "student_name", "code", "message", "elapsedMs"],
    )

    round2_success = [row for row in round2_rows if int(row["code"]) == 200]
    round2_failure = [row for row in round2_rows if int(row["code"]) != 200]
    latencies = [float(row["elapsedMs"]) for row in round2_rows]
    round2_target_capacity = dict((name, capacity) for name, _, capacity in DEFAULT_COURSE_SPECS)[args.round2_target]

    final_selection_results = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(prepared.accounts), args.result_workers))) as executor:
        futures = [executor.submit(fetch_student_selection_result, account, config.base_url) for account in prepared.accounts]
        for future in as_completed(futures):
            final_selection_results.append(future.result())

    final_course_counts = {}
    final_target_confirmed = 0
    total_confirmed_after_round2 = 0
    for item in final_selection_results:
        confirmed = item["confirmed"]
        if not confirmed:
            continue
        total_confirmed_after_round2 += 1
        course_name = str(confirmed.get("courseName"))
        final_course_counts[course_name] = final_course_counts.get(course_name, 0) + 1
        if course_name == args.round2_target:
            final_target_confirmed += 1

    summary = {
        "baseUrl": config.base_url,
        "eventId": event_id,
        "eventName": event_name,
        "studentPrefix": prepared.prefix,
        "studentCount": len(prepared.accounts),
        "firstRound": {
            "preference1": args.round1_pref1,
            "preference2": args.round1_pref2,
            "lotteryDurationMs": round(lottery_duration_ms, 2),
            "firstChoiceConfirmed": first_choice_confirmed,
            "secondChoiceConfirmed": second_choice_confirmed,
            "unsuccessful": len(unsuccessful_accounts),
            "courseConfirmedCounts": round1_course_counts,
        },
        "secondRound": {
            "targetCourse": args.round2_target,
            "targetCourseId": round2_course_id,
            "targetCourseCapacity": round2_target_capacity,
            "concurrentUsers": round2_users,
            "successCount": len(round2_success),
            "failureCount": len(round2_failure),
            "wallTimeMs": round(round2_wall_ms, 2),
            "latencyMs": latency_summary(latencies),
            "sampleFailures": sorted({str(row["message"]) for row in round2_failure})[:5],
            "finalConfirmedInTargetCourse": final_target_confirmed,
            "oversold": final_target_confirmed > round2_target_capacity,
            "oversoldBy": max(0, final_target_confirmed - round2_target_capacity),
        },
        "finalConfirmedCounts": final_course_counts,
        "totalConfirmedAfterRound2": total_confirmed_after_round2,
        "files": {
            "accountsResetXlsx": str(prepared.run_dir / "accounts_reset.xlsx"),
            "round1PreferenceSubmit": str(prepared.run_dir / "round1_preference_submit.csv"),
            "round1ConfirmSubmit": str(prepared.run_dir / "round1_confirm_submit.csv"),
            "round1StudentResults": str(prepared.run_dir / "round1_student_results.csv"),
            "round2ConcurrencyResults": str(prepared.run_dir / "round2_concurrency_results.csv"),
            "summaryJson": str(prepared.run_dir / "summary.json"),
        },
    }

    write_summary(prepared.run_dir, summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
