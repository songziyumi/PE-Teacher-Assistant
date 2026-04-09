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
    class_index_from_student_name,
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


def build_parser() -> argparse.ArgumentParser:
    base_url, base_url_required = env_or_required("COURSE_SELECTION_BASE_URL")
    admin_username, admin_username_required = env_or_required("COURSE_SELECTION_ADMIN_USERNAME")
    admin_password, admin_password_required = env_or_required("COURSE_SELECTION_ADMIN_PASSWORD")

    parser = argparse.ArgumentParser(description="执行羽毛球按班余量回归。")
    parser.add_argument("--base-url", default=base_url, required=base_url_required)
    parser.add_argument("--admin-username", default=admin_username, required=admin_username_required)
    parser.add_argument("--admin-password", default=admin_password, required=admin_password_required)
    parser.add_argument("--accounts-csv", default="scripts/jmeter/data/student_accounts.csv")
    parser.add_argument("--results-dir", default="scripts/regression/results")
    parser.add_argument("--grade-name", default="高一")
    parser.add_argument("--class-count", type=int, default=10)
    parser.add_argument("--target-course", default="羽毛球")
    parser.add_argument("--fallback-course", default="篮球")
    parser.add_argument("--per-class-capacity", type=int, default=4)
    parser.add_argument("--round1-per-class", type=int, default=2)
    parser.add_argument("--round2-compete-per-class", type=int, default=4)
    parser.add_argument("--preference-workers", type=int, default=40)
    parser.add_argument("--confirm-workers", type=int, default=5)
    parser.add_argument("--result-workers", type=int, default=50)
    return parser


def submit_preferences(account, course_map, first_choice, second_choice, base_url):
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    pref1_id = course_map[first_choice]
    pref2_id = course_map[second_choice]
    result1 = client.api_request("POST", f"/api/student/courses/{pref1_id}/prefer?preference=1", token=token)
    result2 = client.api_request("POST", f"/api/student/courses/{pref2_id}/prefer?preference=2", token=token)
    return {
        "username": account.username,
        "student_no": account.student_no,
        "student_name": account.student_name,
        "pref1Course": first_choice,
        "pref1Code": str(result1.get("code")),
        "pref1Message": str(result1.get("message")),
        "pref2Course": second_choice,
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

    prepared = prepare_standard_run(config, "round4_badminton_per_class")
    event_name = f"{prepared.started.strftime('%Y-%m-%d %H:%M:%S')} 羽毛球按班余量回归 {prepared.prefix}"

    accounts_by_class = {index: [] for index in range(1, args.class_count + 1)}
    for account in sorted(prepared.accounts, key=lambda item: item.student_no):
        accounts_by_class[class_index_from_student_name(account.student_name)].append(account)

    round1_accounts = []
    round2_accounts = []
    for class_accounts in accounts_by_class.values():
        round1_accounts.extend(class_accounts[: args.round1_per_class])
        round2_accounts.extend(class_accounts[args.round1_per_class : args.round1_per_class + args.round2_compete_per_class])

    event_id = create_event(prepared.admin_client, event_name, prepared.admin_csrf)
    save_event_students(prepared.admin_client, event_id, prepared.student_ids, prepared.admin_csrf)
    create_courses(prepared.admin_client, event_id, prepared.classes, prepared.admin_csrf, DEFAULT_COURSE_SPECS)
    activate_all_courses(prepared.admin_client, event_id, prepared.admin_csrf)
    start_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    course_map = fetch_course_map_for_current_event(round1_accounts[0], config.base_url)

    pref_submit_rows = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(round1_accounts), args.preference_workers))) as executor:
        futures = [
            executor.submit(
                submit_preferences,
                account,
                course_map,
                args.target_course,
                args.fallback_course,
                config.base_url,
            )
            for account in round1_accounts
        ]
        for future in as_completed(futures):
            pref_submit_rows.append(future.result())
    write_csv(
        prepared.run_dir / "round1_preference_submit.csv",
        pref_submit_rows,
        ["username", "student_no", "student_name", "pref1Course", "pref1Code", "pref1Message", "pref2Course", "pref2Code", "pref2Message"],
    )

    confirm_rows = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(round1_accounts), args.confirm_workers))) as executor:
        futures = [executor.submit(activate_and_confirm_round1, account, config.base_url) for account in round1_accounts]
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

    round1_target_by_class = {index: 0 for index in range(1, args.class_count + 1)}
    unsuccessful_accounts = []
    per_student_rows = []
    for item in selection_results:
        account = item["account"]
        confirmed = item["confirmed"]
        selections = item["selections"]
        class_index = class_index_from_student_name(account.student_name)
        if confirmed and confirmed.get("courseName") == args.target_course:
            round1_target_by_class[class_index] += 1
        if not confirmed:
            unsuccessful_accounts.append(account)
            status = "UNSUCCESSFUL"
        else:
            status = "CONFIRMED"
        per_student_rows.append(
            {
                "username": account.username,
                "student_no": account.student_no,
                "student_name": account.student_name,
                "class_index": class_index,
                "status": status,
                "confirmed_course": confirmed.get("courseName") if confirmed else "",
                "confirmed_preference": confirmed.get("preference") if confirmed else "",
                "selection_json": json.dumps(selections, ensure_ascii=False),
            }
        )

    write_csv(
        prepared.run_dir / "round1_student_results.csv",
        per_student_rows,
        ["username", "student_no", "student_name", "class_index", "status", "confirmed_course", "confirmed_preference", "selection_json"],
    )

    unsuccessful_student_nos = {account.student_no for account in unsuccessful_accounts}
    round2_candidates = [account for account in round2_accounts if account.student_no in unsuccessful_student_nos]
    round2_course_id = course_map[args.target_course]

    round2_tokens = []
    if round2_candidates:
        with ThreadPoolExecutor(max_workers=max(1, min(len(round2_candidates), args.result_workers))) as executor:
            futures = [executor.submit(fetch_round2_token, account, config.base_url) for account in round2_candidates]
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
                    {"class_index": class_index_from_student_name(item["account"].student_name)},
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
        ["username", "student_no", "student_name", "class_index", "code", "message", "elapsedMs"],
    )

    final_selection_results = []
    with ThreadPoolExecutor(max_workers=max(1, min(len(prepared.accounts), args.result_workers))) as executor:
        futures = [executor.submit(fetch_student_selection_result, account, config.base_url) for account in prepared.accounts]
        for future in as_completed(futures):
            final_selection_results.append(future.result())

    final_target_by_class = {index: 0 for index in range(1, args.class_count + 1)}
    round2_success_by_class = {index: 0 for index in range(1, args.class_count + 1)}
    for row in round2_rows:
        if int(row["code"]) == 200:
            round2_success_by_class[int(row["class_index"])] += 1
    for item in final_selection_results:
        confirmed = item["confirmed"]
        if confirmed and confirmed.get("courseName") == args.target_course:
            final_target_by_class[class_index_from_student_name(item["account"].student_name)] += 1

    round2_failures = [row for row in round2_rows if int(row["code"]) != 200]
    per_class_rows = []
    for class_index in range(1, args.class_count + 1):
        per_class_rows.append(
            {
                "class_index": class_index,
                "class_name": f"{class_index}班",
                "round1_confirmed": round1_target_by_class[class_index],
                "round2_success": round2_success_by_class[class_index],
                "final_confirmed": final_target_by_class[class_index],
                "remaining_after_round1": args.per_class_capacity - round1_target_by_class[class_index],
                "oversold": final_target_by_class[class_index] > args.per_class_capacity,
            }
        )
    write_csv(
        prepared.run_dir / "badminton_per_class_summary.csv",
        per_class_rows,
        ["class_index", "class_name", "round1_confirmed", "round2_success", "final_confirmed", "remaining_after_round1", "oversold"],
    )

    summary = {
        "baseUrl": config.base_url,
        "eventId": event_id,
        "eventName": event_name,
        "studentPrefix": prepared.prefix,
        "studentCount": len(prepared.accounts),
        "scenario": {
            "course": args.target_course,
            "capacityMode": "PER_CLASS",
            "perClassCapacity": args.per_class_capacity,
            "round1ApplicantsPerClass": args.round1_per_class,
            "round2CompetitorsPerClass": args.round2_compete_per_class,
        },
        "firstRound": {
            "lotteryDurationMs": round(lottery_duration_ms, 2),
            "confirmedByClass": round1_target_by_class,
            "confirmedTotal": sum(round1_target_by_class.values()),
        },
        "secondRound": {
            "targetCourse": args.target_course,
            "targetCourseId": round2_course_id,
            "concurrentUsers": len(round2_candidates),
            "successCount": sum(round2_success_by_class.values()),
            "failureCount": len(round2_failures),
            "wallTimeMs": round(round2_wall_ms, 2),
            "latencyMs": latency_summary([float(row["elapsedMs"]) for row in round2_rows]),
            "sampleFailures": sorted({str(row["message"]) for row in round2_failures})[:5],
            "successByClass": round2_success_by_class,
        },
        "final": {
            "confirmedByClass": final_target_by_class,
            "confirmedTotal": sum(final_target_by_class.values()),
            "anyOversoldClass": any(item["oversold"] for item in per_class_rows),
        },
        "files": {
            "accountsResetXlsx": str(prepared.run_dir / "accounts_reset.xlsx"),
            "round1PreferenceSubmit": str(prepared.run_dir / "round1_preference_submit.csv"),
            "round1ConfirmSubmit": str(prepared.run_dir / "round1_confirm_submit.csv"),
            "round1StudentResults": str(prepared.run_dir / "round1_student_results.csv"),
            "round2ConcurrencyResults": str(prepared.run_dir / "round2_concurrency_results.csv"),
            "perClassSummary": str(prepared.run_dir / "badminton_per_class_summary.csv"),
            "summaryJson": str(prepared.run_dir / "summary.json"),
        },
    }

    write_summary(prepared.run_dir, summary)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
