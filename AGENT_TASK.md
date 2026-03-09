# Agent Task: Approval

You are working in the `feature/teacher-p1-approval` worktree.

## Goal

Complete the teacher-side P1 first batch for approval workflow:
- batch approval API behavior
- batch approval UI flow
- partial failure handling

## Scope

Primary files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
- `src/main/java/com/pe/assistant/service/MessageService.java`
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
- `mobile/lib/services/teacher_service.dart`
- `mobile/lib/screens/teacher/course_request_center.dart`

## Required Outcomes

- Review and complete `/teacher/course-requests/batch-handle`
- Make partial failures explicit:
  - already processed
  - not owned by current teacher
  - not found
  - invalid message type
- Complete batch approval UI:
  - multi-select
  - select all
  - clear selection
  - batch approve
  - batch reject
  - disable repeated submit while running
  - readable partial failure feedback
- Add regression coverage for:
  - batch approve success
  - batch reject success
  - partial failure
  - duplicated message IDs
  - blank and overlong remark handling

## Hard Boundaries

Do not modify:
- `mobile/lib/screens/teacher/teacher_student_list.dart`
- `mobile/lib/models/student.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
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
approval: <change>
```
