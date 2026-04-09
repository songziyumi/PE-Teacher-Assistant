import argparse
import json
from pathlib import Path

from course_selection_regression_lib import (
    RegressionConfig,
    activate_all_courses,
    activate_and_confirm_round1,
    api_login_student,
    create_courses,
    create_event,
    env_or_required,
    fetch_course_map_for_current_event,
    prepare_standard_run,
    process_round1,
    save_event_students,
    start_round1,
    write_summary,
)


COURSE_SPECS = [
    ("篮球", "GLOBAL", 1),
    ("足球", "GLOBAL", 1),
]
ROUND1_COURSE = "篮球"
ROUND2_TARGET = "足球"


def build_parser() -> argparse.ArgumentParser:
    base_url, base_url_required = env_or_required("COURSE_SELECTION_BASE_URL")
    admin_username, admin_username_required = env_or_required("COURSE_SELECTION_ADMIN_USERNAME")
    admin_password, admin_password_required = env_or_required("COURSE_SELECTION_ADMIN_PASSWORD")

    parser = argparse.ArgumentParser(description="执行“退课后再次进入第二轮抢课”回归验证")
    parser.add_argument("--base-url", default=base_url, required=base_url_required)
    parser.add_argument("--admin-username", default=admin_username, required=admin_username_required)
    parser.add_argument("--admin-password", default=admin_password, required=admin_password_required)
    parser.add_argument("--accounts-csv", default="scripts/jmeter/data/student_accounts.csv")
    parser.add_argument("--results-dir", default="scripts/regression/results")
    parser.add_argument("--grade-name", default="高一")
    parser.add_argument("--class-count", type=int, default=10)
    parser.add_argument("--round1-course", default=ROUND1_COURSE)
    parser.add_argument("--round2-target", default=ROUND2_TARGET)
    return parser


def submit_round1_preference(account, base_url: str, course_id: int) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    result = client.api_request("POST", f"/api/student/courses/{course_id}/prefer?preference=1", token=token)
    return {
        "code": int(result.get("code", 0)),
        "message": str(result.get("message", "")),
    }


def fetch_my_selections(account, base_url: str) -> list:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request("GET", "/api/student/my-selections", token=token)
    return payload.get("data", [])


def drop_selection(account, base_url: str, selection_id: int) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request("DELETE", f"/api/student/selections/{selection_id}", token=token)
    return {
        "code": int(payload.get("code", 0)),
        "message": str(payload.get("message", "")),
    }


def select_round2_course(account, base_url: str, course_id: int) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request("POST", f"/api/student/courses/{course_id}/select", token=token)
    return {
        "code": int(payload.get("code", 0)),
        "message": str(payload.get("message", "")),
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

    prepared = prepare_standard_run(config, "round2_drop_reselect")
    account = prepared.accounts[0]
    event_name = f"{prepared.started.strftime('%Y-%m-%d %H:%M:%S')} 退课后再次进入第二轮回归 {prepared.prefix}"
    event_id = create_event(prepared.admin_client, event_name, prepared.admin_csrf)

    save_event_students(prepared.admin_client, event_id, prepared.student_ids, prepared.admin_csrf)
    create_courses(prepared.admin_client, event_id, prepared.classes, prepared.admin_csrf, COURSE_SPECS)
    activate_all_courses(prepared.admin_client, event_id, prepared.admin_csrf)
    start_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    course_map = fetch_course_map_for_current_event(account, config.base_url)
    round1_submit_result = submit_round1_preference(account, config.base_url, course_map[args.round1_course])
    round1_confirm_result = activate_and_confirm_round1(account, config.base_url)
    lottery_duration_ms = process_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    before_drop_selections = fetch_my_selections(account, config.base_url)
    confirmed_round1_selection = next(
        (item for item in before_drop_selections if item.get("courseName") == args.round1_course and item.get("status") == "CONFIRMED"),
        None,
    )
    if not confirmed_round1_selection:
        raise RuntimeError(f"第一轮未确认课程：{args.round1_course}")
    if not confirmed_round1_selection.get("canDrop"):
        raise RuntimeError("第一轮确认记录在第二轮期间未标记为可退课")

    drop_result = drop_selection(account, config.base_url, int(confirmed_round1_selection["id"]))
    round2_select_result = select_round2_course(account, config.base_url, course_map[args.round2_target])
    final_selections = fetch_my_selections(account, config.base_url)
    final_confirmed = [item for item in final_selections if item.get("status") == "CONFIRMED"]

    summary = {
        "eventId": event_id,
        "eventName": event_name,
        "student": {
            "username": account.username,
            "studentNo": account.student_no,
            "studentName": account.student_name,
        },
        "round1Course": args.round1_course,
        "round2Target": args.round2_target,
        "round1SubmitResult": round1_submit_result,
        "round1ConfirmResult": round1_confirm_result,
        "lotteryDurationMs": round(lottery_duration_ms, 2),
        "beforeDropSelections": before_drop_selections,
        "dropResult": drop_result,
        "round2SelectResult": round2_select_result,
        "finalSelections": final_selections,
        "finalConfirmedCourse": final_confirmed[0].get("courseName") if len(final_confirmed) == 1 else None,
        "dropAndReselectPassed": (
            int(drop_result["code"]) == 200
            and int(round2_select_result["code"]) == 200
            and len(final_confirmed) == 1
            and final_confirmed[0].get("courseName") == args.round2_target
        ),
    }

    summary_path = write_summary(prepared.run_dir, summary)
    print(json.dumps({"summaryPath": str(summary_path), **summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
