import json
import statistics
import sys
import threading
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
if str(CURRENT_DIR) not in sys.path:
    sys.path.insert(0, str(CURRENT_DIR))

from run_round1_round2_tests import (  # noqa: E402
    ACCOUNTS_CSV,
    ADMIN_PASSWORD,
    ADMIN_USERNAME,
    BASE_URL,
    CONFIRM_WORKERS,
    PREFERENCE_WORKERS,
    RESULT_WORKERS,
    Account,
    RemoteClient,
    activate_and_confirm_round1,
    api_login_student,
    create_courses,
    create_event,
    ensure_dir,
    fetch_classes,
    fetch_course_map_for_current_event,
    fetch_student_selection_result,
    fetch_target_students,
    infer_prefix,
    load_accounts,
    parse_csrf_token,
    percentile,
    process_round1,
    refresh_accounts_from_export,
    reset_student_passwords,
    save_event_students,
    start_round1,
    write_csv,
)


RESULT_DIR = Path("temp/test-results")
TARGET_COURSE = "羽毛球"
PER_CLASS_CAPACITY = 4
FULL_CLASSES = range(1, 6)
OPEN_CLASSES = range(6, 11)


def class_index_from_name(student_name: str) -> int:
    parts = student_name.split("-")
    if len(parts) < 3:
        raise RuntimeError(f"无法从学生姓名解析班级：{student_name}")
    return int(parts[1])


def submit_preferences(account: Account, course_map: dict, first_choice: str, second_choice: str) -> dict:
    client, login = api_login_student(account)
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


def fetch_round2_token(account: Account, candidate_type: str) -> dict:
    client, login = api_login_student(account)
    return {"account": account, "token": login["data"]["token"], "candidate_type": candidate_type}


def second_round_select_with_token(
    account: Account,
    token: str,
    course_id: int,
    candidate_type: str,
    start_event: threading.Event,
) -> dict:
    client = RemoteClient(BASE_URL)
    start_event.wait(timeout=30)
    started_at = time.perf_counter()
    try:
        payload = client.api_request("POST", f"/api/student/courses/{course_id}/select", token=token)
        elapsed_ms = (time.perf_counter() - started_at) * 1000
        return {
            "username": account.username,
            "student_no": account.student_no,
            "student_name": account.student_name,
            "class_index": class_index_from_name(account.student_name),
            "candidate_type": candidate_type,
            "code": int(payload.get("code", 0)),
            "message": str(payload.get("message", "")),
            "elapsedMs": round(elapsed_ms, 2),
        }
    except Exception as exc:
        elapsed_ms = (time.perf_counter() - started_at) * 1000
        return {
            "username": account.username,
            "student_no": account.student_no,
            "student_name": account.student_name,
            "class_index": class_index_from_name(account.student_name),
            "candidate_type": candidate_type,
            "code": -1,
            "message": str(exc),
            "elapsedMs": round(elapsed_ms, 2),
        }


def main() -> None:
    started = datetime.now()
    ensure_dir(RESULT_DIR)
    accounts = load_accounts(ACCOUNTS_CSV)
    prefix = infer_prefix(accounts)
    run_tag = started.strftime("%Y%m%d_%H%M%S")
    run_dir = RESULT_DIR / f"round5_badminton_cross_class_{run_tag}"
    ensure_dir(run_dir)

    admin_client = RemoteClient(BASE_URL)
    admin_client.login_web(ADMIN_USERNAME, ADMIN_PASSWORD)
    admin_page, _ = admin_client.get_text("/admin/courses")
    admin_csrf = parse_csrf_token(admin_page)
    admin_token = admin_client.api_login(ADMIN_USERNAME, ADMIN_PASSWORD)["data"]["token"]

    classes = fetch_classes(admin_client, admin_token)
    students = fetch_target_students(admin_client, admin_token, classes, prefix, len(accounts))
    student_ids = [int(item["id"]) for item in students]
    reset_student_passwords(admin_client, student_ids, admin_csrf)
    exported_accounts = refresh_accounts_from_export(admin_client, run_dir / "accounts_reset.xlsx")
    account_map = {account.student_no: account for account in exported_accounts}
    accounts = [account_map[account.student_no] for account in accounts if account.student_no in account_map]

    accounts_by_class: dict[int, list[Account]] = {index: [] for index in range(1, 11)}
    for account in sorted(accounts, key=lambda item: item.student_no):
        accounts_by_class[class_index_from_name(account.student_name)].append(account)

    round1_accounts: list[Account] = []
    round2_own_accounts: list[Account] = []
    round2_cross_accounts: list[Account] = []
    for class_index, class_accounts in accounts_by_class.items():
        if class_index in FULL_CLASSES:
            round1_accounts.extend(class_accounts[:4])
            round2_cross_accounts.extend(class_accounts[4:6])
        else:
            round1_accounts.extend(class_accounts[:2])
            round2_own_accounts.extend(class_accounts[2:4])

    event_name = f"{started.strftime('%Y-%m-%d %H:%M:%S')} 第五次羽毛球跨班混抢验证 {prefix}"
    event_id = create_event(admin_client, event_name, admin_csrf)
    save_event_students(admin_client, event_id, student_ids, admin_csrf)
    create_courses(admin_client, event_id, classes, admin_csrf)
    detail_html, _ = admin_client.get_text(f"/admin/courses/{event_id}/detail?tab=courses")
    admin_csrf = parse_csrf_token(detail_html)
    from run_round1_round2_tests import activate_all_courses  # noqa: E402
    admin_csrf = activate_all_courses(admin_client, event_id, admin_csrf)
    start_round1(admin_client, event_id, admin_csrf)

    course_map = fetch_course_map_for_current_event(round1_accounts[0])

    pref_submit_rows: list[dict] = []
    with ThreadPoolExecutor(max_workers=PREFERENCE_WORKERS) as executor:
        futures = [
            executor.submit(submit_preferences, account, course_map, TARGET_COURSE, "篮球")
            for account in round1_accounts
        ]
        for future in as_completed(futures):
            pref_submit_rows.append(future.result())
    write_csv(
        run_dir / "round1_preference_submit.csv",
        pref_submit_rows,
        ["username", "student_no", "student_name", "pref1Course", "pref1Code", "pref1Message", "pref2Course", "pref2Code", "pref2Message"],
    )

    confirm_rows: list[dict] = []
    with ThreadPoolExecutor(max_workers=CONFIRM_WORKERS) as executor:
        futures = [executor.submit(activate_and_confirm_round1, account) for account in round1_accounts]
        for future in as_completed(futures):
            confirm_rows.append(future.result())
    write_csv(
        run_dir / "round1_confirm_submit.csv",
        confirm_rows,
        ["username", "student_no", "student_name", "new_password", "status"],
    )

    lottery_duration_ms = process_round1(admin_client, event_id, admin_csrf)

    selection_results = []
    with ThreadPoolExecutor(max_workers=RESULT_WORKERS) as executor:
        futures = [executor.submit(fetch_student_selection_result, account) for account in accounts]
        for future in as_completed(futures):
            selection_results.append(future.result())

    round1_badminton_by_class = {index: 0 for index in range(1, 11)}
    unsuccessful_student_nos = set()
    per_student_rows = []
    for item in selection_results:
        account = item["account"]
        confirmed = item["confirmed"]
        selections = item["selections"]
        class_index = class_index_from_name(account.student_name)
        if confirmed and confirmed.get("courseName") == TARGET_COURSE:
            round1_badminton_by_class[class_index] += 1
        if not confirmed:
            unsuccessful_student_nos.add(account.student_no)
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
        run_dir / "round1_student_results.csv",
        per_student_rows,
        ["username", "student_no", "student_name", "class_index", "status", "confirmed_course", "confirmed_preference", "selection_json"],
    )

    round2_own_candidates = [account for account in round2_own_accounts if account.student_no in unsuccessful_student_nos]
    round2_cross_candidates = [account for account in round2_cross_accounts if account.student_no in unsuccessful_student_nos]
    round2_course_id = course_map[TARGET_COURSE]

    round2_tokens = []
    with ThreadPoolExecutor(max_workers=min(len(round2_own_candidates) + len(round2_cross_candidates), RESULT_WORKERS)) as executor:
        futures = []
        futures.extend(executor.submit(fetch_round2_token, account, "own_class") for account in round2_own_candidates)
        futures.extend(executor.submit(fetch_round2_token, account, "cross_class") for account in round2_cross_candidates)
        for future in as_completed(futures):
            round2_tokens.append(future.result())

    start_event = threading.Event()
    round2_started_at = time.perf_counter()
    round2_rows = []
    with ThreadPoolExecutor(max_workers=max(1, len(round2_tokens))) as executor:
        futures = [
            executor.submit(
                second_round_select_with_token,
                item["account"],
                item["token"],
                round2_course_id,
                item["candidate_type"],
                start_event,
            )
            for item in round2_tokens
        ]
        start_event.set()
        for future in as_completed(futures):
            round2_rows.append(future.result())
    round2_wall_ms = (time.perf_counter() - round2_started_at) * 1000
    write_csv(
        run_dir / "round2_concurrency_results.csv",
        round2_rows,
        ["username", "student_no", "student_name", "class_index", "candidate_type", "code", "message", "elapsedMs"],
    )

    final_selection_results = []
    with ThreadPoolExecutor(max_workers=RESULT_WORKERS) as executor:
        futures = [executor.submit(fetch_student_selection_result, account) for account in accounts]
        for future in as_completed(futures):
            final_selection_results.append(future.result())

    final_badminton_by_class = {index: 0 for index in range(1, 11)}
    for item in final_selection_results:
        confirmed = item["confirmed"]
        if confirmed and confirmed.get("courseName") == TARGET_COURSE:
            final_badminton_by_class[class_index_from_name(item["account"].student_name)] += 1

    own_success = [row for row in round2_rows if row["candidate_type"] == "own_class" and int(row["code"]) == 200]
    own_failure = [row for row in round2_rows if row["candidate_type"] == "own_class" and int(row["code"]) != 200]
    cross_success = [row for row in round2_rows if row["candidate_type"] == "cross_class" and int(row["code"]) == 200]
    cross_failure = [row for row in round2_rows if row["candidate_type"] == "cross_class" and int(row["code"]) != 200]
    latencies = [float(row["elapsedMs"]) for row in round2_rows]

    per_class_rows = []
    for class_index in range(1, 11):
        candidate_type = "full_class" if class_index in FULL_CLASSES else "open_class"
        per_class_rows.append(
            {
                "class_index": class_index,
                "class_name": f"{class_index}班",
                "class_type": candidate_type,
                "round1_confirmed": round1_badminton_by_class[class_index],
                "final_confirmed": final_badminton_by_class[class_index],
                "remaining_after_round1": PER_CLASS_CAPACITY - round1_badminton_by_class[class_index],
                "should_accept_round2": class_index in OPEN_CLASSES,
                "oversold": final_badminton_by_class[class_index] > PER_CLASS_CAPACITY,
            }
        )
    write_csv(
        run_dir / "badminton_cross_class_summary.csv",
        per_class_rows,
        ["class_index", "class_name", "class_type", "round1_confirmed", "final_confirmed", "remaining_after_round1", "should_accept_round2", "oversold"],
    )

    summary = {
        "baseUrl": BASE_URL,
        "eventId": event_id,
        "eventName": event_name,
        "studentPrefix": prefix,
        "studentCount": len(accounts),
        "scenario": {
            "course": TARGET_COURSE,
            "capacityMode": "PER_CLASS",
            "perClassCapacity": PER_CLASS_CAPACITY,
            "fullClasses": list(FULL_CLASSES),
            "openClasses": list(OPEN_CLASSES),
            "round1ApplicantsPerFullClass": 4,
            "round1ApplicantsPerOpenClass": 2,
            "round2OwnClassCompetitorsPerOpenClass": 2,
            "round2CrossClassCompetitorsPerFullClass": 2,
        },
        "firstRound": {
            "lotteryDurationMs": round(lottery_duration_ms, 2),
            "badmintonConfirmedByClass": round1_badminton_by_class,
            "badmintonConfirmedTotal": sum(round1_badminton_by_class.values()),
        },
        "secondRound": {
            "targetCourse": TARGET_COURSE,
            "targetCourseId": round2_course_id,
            "concurrentUsers": len(round2_rows),
            "ownClassSuccessCount": len(own_success),
            "ownClassFailureCount": len(own_failure),
            "crossClassSuccessCount": len(cross_success),
            "crossClassFailureCount": len(cross_failure),
            "wallTimeMs": round(round2_wall_ms, 2),
            "latencyMs": {
                "min": round(min(latencies), 2) if latencies else 0,
                "avg": round(statistics.mean(latencies), 2) if latencies else 0,
                "p50": round(percentile(latencies, 0.50), 2) if latencies else 0,
                "p90": round(percentile(latencies, 0.90), 2) if latencies else 0,
                "p95": round(percentile(latencies, 0.95), 2) if latencies else 0,
                "max": round(max(latencies), 2) if latencies else 0,
            },
            "sampleCrossClassFailures": sorted({str(row["message"]) for row in cross_failure})[:5],
            "crossClassLeak": len(cross_success) > 0,
        },
        "final": {
            "badmintonConfirmedByClass": final_badminton_by_class,
            "badmintonConfirmedTotal": sum(final_badminton_by_class.values()),
            "anyOversoldClass": any(item["oversold"] for item in per_class_rows),
        },
        "files": {
            "accountsResetXlsx": str(run_dir / "accounts_reset.xlsx"),
            "round1PreferenceSubmit": str(run_dir / "round1_preference_submit.csv"),
            "round1ConfirmSubmit": str(run_dir / "round1_confirm_submit.csv"),
            "round1StudentResults": str(run_dir / "round1_student_results.csv"),
            "round2ConcurrencyResults": str(run_dir / "round2_concurrency_results.csv"),
            "perClassSummary": str(run_dir / "badminton_cross_class_summary.csv"),
            "summaryJson": str(run_dir / "summary.json"),
        },
    }

    summary_path = run_dir / "summary.json"
    summary_path.write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
