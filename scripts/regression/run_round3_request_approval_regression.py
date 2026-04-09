import argparse
import json
import re
import time
import urllib.parse
from pathlib import Path

from course_selection_regression_lib import (
    RegressionConfig,
    RemoteClient,
    activate_all_courses,
    activate_and_confirm_round1,
    api_login_student,
    create_event,
    env_or_required,
    fetch_course_map_for_current_event,
    parse_csrf_token,
    prepare_standard_run,
    process_round1,
    save_event_students,
    start_round1,
    write_summary,
)


COURSE_NAME = "第三轮测试课程"
TEACHER_PASSWORD = "abc127!!!"


def build_parser() -> argparse.ArgumentParser:
    base_url, base_url_required = env_or_required("COURSE_SELECTION_BASE_URL")
    admin_username, admin_username_required = env_or_required("COURSE_SELECTION_ADMIN_USERNAME")
    admin_password, admin_password_required = env_or_required("COURSE_SELECTION_ADMIN_PASSWORD")

    parser = argparse.ArgumentParser(description="执行“第三轮申请 + 教师审批”回归验证")
    parser.add_argument("--base-url", default=base_url, required=base_url_required)
    parser.add_argument("--admin-username", default=admin_username, required=admin_username_required)
    parser.add_argument("--admin-password", default=admin_password, required=admin_password_required)
    parser.add_argument("--accounts-csv", default="scripts/jmeter/data/student_accounts.csv")
    parser.add_argument("--results-dir", default="scripts/regression/results")
    parser.add_argument("--grade-name", default="高一")
    parser.add_argument("--class-count", type=int, default=10)
    parser.add_argument("--course-name", default=COURSE_NAME)
    parser.add_argument("--teacher-password", default=TEACHER_PASSWORD)
    parser.add_argument("--auto-close-existing-events", action="store_true")
    return parser


def close_existing_events(client: RemoteClient, csrf: str) -> list:
    page, _ = client.get_text("/admin/courses")
    event_ids = sorted({int(match) for match in re.findall(r"/admin/courses/events/(\d+)/close", page)})
    for event_id in event_ids:
        client.post_form_quick(f"/admin/courses/events/{event_id}/close", [("_csrf", csrf)])
    return event_ids


def create_teacher(client: RemoteClient, csrf: str, prefix: str, password: str) -> dict:
    phone = f"19{int(time.time() * 1000) % 1_000_000_000:09d}"
    name = f"{prefix}-R3教师-{phone[-4:]}"
    client.post_form_quick(
        "/admin/teachers/add",
        [
            ("_csrf", csrf),
            ("name", name),
            ("password", password),
            ("phone", phone),
            ("accountType", "TEACHER"),
        ],
    )
    page, _ = client.get_text(f"/admin/teachers?phone={urllib.parse.quote_plus(phone)}")
    match = re.search(r"/admin/teachers/reset-password/(\d+)", page)
    if not match:
        raise RuntimeError(f"未找到新建教师账号：{phone}")
    return {
        "id": int(match.group(1)),
        "username": phone,
        "password": password,
        "name": name,
    }


def create_teacher_course(client: RemoteClient, event_id: int, csrf: str, course_name: str, teacher_id: int) -> None:
    client.post_form_quick(
        f"/admin/courses/{event_id}/courses/save",
        [
            ("_csrf", csrf),
            ("name", course_name),
            ("description", f"{course_name} 自动化回归测试课程"),
            ("teacherId", str(teacher_id)),
            ("capacityMode", "GLOBAL"),
            ("totalCapacity", "3"),
        ],
    )


def submit_round1_preference(account, base_url: str, course_id: int) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request("POST", f"/api/student/courses/{course_id}/prefer?preference=1", token=token)
    return {
        "code": int(payload.get("code", 0)),
        "message": str(payload.get("message", "")),
    }


def fetch_requestable_snapshot(account, base_url: str) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request("GET", "/api/student/courses/requestable", token=token)
    return payload.get("data", {})


def send_course_request(account, base_url: str, course_id: int, content: str) -> dict:
    client, login = api_login_student(account, base_url)
    token = login["data"]["token"]
    payload = client.api_request(
        "POST",
        f"/api/student/courses/{course_id}/request",
        token=token,
        body={"content": content},
    )
    return {
        "code": int(payload.get("code", 0)),
        "message": str(payload.get("message", "")),
    }


def api_login_teacher(base_url: str, username: str, password: str) -> tuple[RemoteClient, str]:
    client = RemoteClient(base_url)
    payload = client.api_login(username, password)
    return client, payload["data"]["token"]


def fetch_teacher_pending_requests(client: RemoteClient, token: str) -> list:
    payload = client.api_request("GET", "/api/teacher/course-requests?status=PENDING", token=token)
    return payload.get("data", [])


def handle_teacher_request(client: RemoteClient, token: str, request_id: int, approve: bool, remark: str) -> dict:
    action = "approve" if approve else "reject"
    payload = client.api_request(
        "POST",
        f"/api/teacher/course-requests/{request_id}/{action}",
        token=token,
        body={"remark": remark},
    )
    return {
        "code": int(payload.get("code", 0)),
        "message": str(payload.get("message", "")),
    }


def fetch_teacher_request_detail(client: RemoteClient, token: str, request_id: int) -> dict:
    payload = client.api_request("GET", f"/api/teacher/course-requests/{request_id}", token=token)
    return payload.get("data", {})


def find_request_id(requests: list, sender_name: str, course_id: int) -> int:
    for request in requests:
        if request.get("senderName") == sender_name and int(request.get("relatedCourseId", 0)) == course_id:
            return int(request["id"])
    raise RuntimeError(f"未找到学生 {sender_name} 对课程 {course_id} 的第三轮申请")


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

    prepared = prepare_standard_run(config, "round3_request_approval")
    seed_account = prepared.accounts[0]
    approve_account = prepared.accounts[1]
    reject_account = prepared.accounts[2]
    if args.auto_close_existing_events:
        closed_event_ids = close_existing_events(prepared.admin_client, prepared.admin_csrf)
        admin_page, _ = prepared.admin_client.get_text("/admin/courses")
        prepared.admin_csrf = parse_csrf_token(admin_page)
    else:
        closed_event_ids = []

    teacher = create_teacher(prepared.admin_client, prepared.admin_csrf, prepared.prefix, args.teacher_password)
    event_name = f"{prepared.started.strftime('%Y-%m-%d %H:%M:%S')} 第三轮申请与教师审批回归 {prepared.prefix}"
    event_id = create_event(prepared.admin_client, event_name, prepared.admin_csrf)

    save_event_students(prepared.admin_client, event_id, prepared.student_ids, prepared.admin_csrf)
    create_teacher_course(prepared.admin_client, event_id, prepared.admin_csrf, args.course_name, teacher["id"])
    activate_all_courses(prepared.admin_client, event_id, prepared.admin_csrf)
    start_round1(prepared.admin_client, event_id, prepared.admin_csrf)

    course_map = fetch_course_map_for_current_event(seed_account, config.base_url)
    course_id = course_map[args.course_name]
    seed_preference_result = submit_round1_preference(seed_account, config.base_url, course_id)
    seed_confirm_result = activate_and_confirm_round1(seed_account, config.base_url)
    lottery_duration_ms = process_round1(prepared.admin_client, event_id, prepared.admin_csrf)
    prepared.admin_client.post_form_quick(f"/admin/courses/events/{event_id}/close", [("_csrf", prepared.admin_csrf)])

    approve_before = fetch_requestable_snapshot(approve_account, config.base_url)
    reject_before = fetch_requestable_snapshot(reject_account, config.base_url)
    approve_submit = send_course_request(approve_account, config.base_url, course_id, "希望补选该课程，请老师审批。")
    reject_submit = send_course_request(reject_account, config.base_url, course_id, "本轮申请验证-预计被拒绝。")

    teacher_client, teacher_token = api_login_teacher(config.base_url, teacher["username"], teacher["password"])
    pending_requests = fetch_teacher_pending_requests(teacher_client, teacher_token)
    approve_request_id = find_request_id(pending_requests, approve_account.student_name, course_id)
    reject_request_id = find_request_id(pending_requests, reject_account.student_name, course_id)

    approve_handle = handle_teacher_request(teacher_client, teacher_token, approve_request_id, True, "同意补选。")
    reject_handle = handle_teacher_request(teacher_client, teacher_token, reject_request_id, False, "本次先不调整。")
    approve_detail = fetch_teacher_request_detail(teacher_client, teacher_token, approve_request_id)
    reject_detail = fetch_teacher_request_detail(teacher_client, teacher_token, reject_request_id)
    approve_after = fetch_requestable_snapshot(approve_account, config.base_url)
    reject_after = fetch_requestable_snapshot(reject_account, config.base_url)

    rejected_course_snapshot = next(
        (item for item in reject_after.get("courses", []) if int(item.get("id", 0)) == course_id),
        {},
    )

    summary = {
        "eventId": event_id,
        "eventName": event_name,
        "courseId": course_id,
        "courseName": args.course_name,
        "teacher": {
            "id": teacher["id"],
            "username": teacher["username"],
            "name": teacher["name"],
        },
        "seedStudent": {
            "studentNo": seed_account.student_no,
            "studentName": seed_account.student_name,
            "preferenceResult": seed_preference_result,
            "confirmResult": seed_confirm_result,
        },
        "lotteryDurationMs": round(lottery_duration_ms, 2),
        "closedExistingEventIds": closed_event_ids,
        "approveStudent": {
            "studentNo": approve_account.student_no,
            "studentName": approve_account.student_name,
            "requestableBefore": approve_before,
            "submitResult": approve_submit,
            "teacherHandleResult": approve_handle,
            "teacherRequestDetail": approve_detail,
            "requestableAfter": approve_after,
        },
        "rejectStudent": {
            "studentNo": reject_account.student_no,
            "studentName": reject_account.student_name,
            "requestableBefore": reject_before,
            "submitResult": reject_submit,
            "teacherHandleResult": reject_handle,
            "teacherRequestDetail": reject_detail,
            "requestableAfter": reject_after,
        },
        "round3ApprovalPassed": (
            int(approve_submit["code"]) == 200
            and int(reject_submit["code"]) == 200
            and int(approve_handle["code"]) == 200
            and int(reject_handle["code"]) == 200
            and approve_detail.get("status") == "APPROVED"
            and reject_detail.get("status") == "REJECTED"
            and approve_after.get("canRequest") is False
            and reject_after.get("canRequest") is True
            and rejected_course_snapshot.get("requestStatus") == "REJECTED"
        ),
    }

    summary_path = write_summary(prepared.run_dir, summary)
    print(json.dumps({"summaryPath": str(summary_path), **summary}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
