# Agent Task: Messages

You are working in the `feature/teacher-p1-messages` worktree.

## Goal

Complete the teacher-side P1 first batch for message center enhancement:
- unread filtering
- type filtering
- read-state behavior
- course request jump fallback

## Scope

Primary files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
- `src/main/java/com/pe/assistant/service/MessageService.java`
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
- `mobile/lib/services/teacher_service.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
- `mobile/lib/models/teacher_message.dart`

## Required Outcomes

- Confirm and stabilize combined behavior of:
  - `unreadOnly`
  - `type`
- Support stable filtering for:
  - `ALL`
  - `GENERAL`
  - `COURSE_REQUEST`
- Keep read-state feedback immediate in UI
- Add refresh behavior
- Add graceful fallback when a course-request jump target no longer exists
- Add regression coverage for:
  - invalid `type`
  - unread count/read-state consistency
  - stable filter results

## Hard Boundaries

Do not modify:
- `mobile/lib/screens/teacher/teacher_student_list.dart`
- `mobile/lib/models/student.dart`
- `mobile/lib/screens/teacher/course_request_center.dart`
- `mobile/lib/screens/teacher/course_request_detail.dart`
- `src/main/java/com/pe/assistant/service/StudentService.java`

## Validation

Run:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" -Dtest=TeacherApiControllerRegressionTest test
dart analyze --no-fatal-warnings
```

## Commit Format

Use:

```text
messages: <change>
```
