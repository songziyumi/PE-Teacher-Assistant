# Agent Task: Students

You are working in the `feature/teacher-p1-students` worktree.

## Goal

Complete the teacher-side P1 first batch for student management:
- multi-filter student list verification and completion
- batch student operations

## Scope

Primary files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
- `src/main/java/com/pe/assistant/service/StudentService.java`
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
- `mobile/lib/services/teacher_service.dart`
- `mobile/lib/screens/teacher/teacher_student_list.dart`

## Required Outcomes

- Confirm all filters work correctly:
  - `name`
  - `studentNo`
  - `adminClassId`
  - `electiveClass`
  - `studentStatus`
- Complete batch student actions:
  - batch update student status
  - batch assign elective class
  - batch clear elective class
- Show readable batch result feedback:
  - `totalCount`
  - `successCount`
  - `failedCount`
  - failed item summary or IDs
- Add regression coverage for:
  - empty selection
  - duplicated student IDs
  - `electiveClass = null`
  - cross-school rejection

## Hard Boundaries

Do not modify:
- `mobile/lib/screens/teacher/course_request_center.dart`
- `mobile/lib/screens/teacher/course_request_detail.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
- `mobile/lib/models/teacher_message.dart`
- `src/main/java/com/pe/assistant/service/MessageService.java`

## Validation

Run:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" -Dtest=TeacherApiControllerRegressionTest test
dart analyze --no-fatal-warnings
```

## Commit Format

Use:

```text
students: <change>
```
