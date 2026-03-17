# Worktree Collaboration Rules

## Scope

This document defines branch ownership, file ownership, merge order, and conflict rules for the teacher-side P1 parallel workstreams.

Active workstreams:
- `feature/teacher-p1-students`
- `feature/teacher-p1-approval`
- `feature/teacher-p1-messages`
- `feature/teacher-p1-integration`

## Shared Principles

- `students`, `approval`, and `messages` are business delivery branches.
- `integration` is for merge prep, conflict resolution, schema alignment, and final verification.
- Do not expand scope across domains "while already in the file".
- For shared files, only change methods owned by your workstream.
- Do not remove another workstream's regression coverage during conflict resolution.

## Merge Order

Cherry-pick into `feature/teacher-p1-integration` in this order:

1. `feature/teacher-p1-students`
2. `feature/teacher-p1-approval`
3. `feature/teacher-p1-messages`
4. integration-only cleanup and validation

After integration is stable, merge `feature/teacher-p1-integration` back into `feature/course-selection-next`.

## Commit Message Template

Use:

```text
<track>: <change>
```

Valid `<track>` values:
- `students`
- `approval`
- `messages`
- `integration`

Examples:
- `students: add batch student update endpoints`
- `approval: add batch course request handling api`
- `messages: add teacher message filters`
- `integration: align batch result response schema`

## Unified Batch Response Recommendation

Prefer this response shape for batch actions:

```json
{
  "totalCount": 10,
  "successCount": 8,
  "failedCount": 2,
  "failedItems": [
    { "id": 101, "reason": "..." }
  ]
}
```

Domain-specific ID lists such as `studentIds` may remain, but `failedItems` should be added when useful so the frontend can render failures consistently.

## File Ownership

### `students` workstream

Hard-forbidden files:
- `mobile/lib/screens/teacher/course_request_center.dart`
- `mobile/lib/screens/teacher/course_request_detail.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
- `mobile/lib/models/teacher_message.dart`
- `src/main/java/com/pe/assistant/service/MessageService.java`

Restricted shared files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
  - Only student filtering and batch student operation methods
- `mobile/lib/services/teacher_service.dart`
  - Only student query and batch student operation methods
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
  - Only student-domain regression coverage

### `approval` workstream

Hard-forbidden files:
- `mobile/lib/screens/teacher/teacher_student_list.dart`
- `mobile/lib/models/student.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
- `src/main/java/com/pe/assistant/service/StudentService.java`

Restricted shared files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
  - Only approval batch handling methods
- `mobile/lib/services/teacher_service.dart`
  - Only approval list, detail, and batch approval methods
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
  - Only approval-domain regression coverage
- `src/main/java/com/pe/assistant/service/MessageService.java`
  - Only approval state flow and batch approval behavior

### `messages` workstream

Hard-forbidden files:
- `mobile/lib/screens/teacher/teacher_student_list.dart`
- `mobile/lib/models/student.dart`
- `mobile/lib/screens/teacher/course_request_center.dart`
- `mobile/lib/screens/teacher/course_request_detail.dart`
- `src/main/java/com/pe/assistant/service/StudentService.java`

Restricted shared files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
  - Only message list, unread count, read-state, and filter methods
- `mobile/lib/services/teacher_service.dart`
  - Only message-related methods
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
  - Only message-domain regression coverage
- `src/main/java/com/pe/assistant/service/MessageService.java`
  - Only message filtering, read-state, and jump fallback behavior

### `integration` workstream

Hard-forbidden files:
- `mobile/lib/screens/teacher/teacher_student_list.dart`
- `mobile/lib/screens/teacher/course_request_center.dart`
- `mobile/lib/screens/teacher/course_request_detail.dart`
- `mobile/lib/screens/teacher/teacher_message_center.dart`
- `src/main/java/com/pe/assistant/service/StudentService.java`
- `src/main/java/com/pe/assistant/service/MessageService.java`

Restricted shared files:
- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
  - Only naming alignment and compatibility cleanup
- `mobile/lib/services/teacher_service.dart`
  - Only response alignment and shared error handling cleanup
- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
  - Only conflict merge and assertion completion

Allowed additions:
- Integration notes
- Validation checklists
- Merge tracking documents

## Shared File Primary Ownership

Primary ownership for hot files:

- `src/main/java/com/pe/assistant/controller/api/TeacherApiController.java`
  - student methods: `students`
  - approval methods: `approval`
  - message methods: `messages`
  - schema cleanup: `integration`

- `mobile/lib/services/teacher_service.dart`
  - student methods: `students`
  - approval methods: `approval`
  - message methods: `messages`
  - dedup/alignment cleanup: `integration`

- `src/test/java/com/pe/assistant/controller/api/TeacherApiControllerRegressionTest.java`
  - each workstream adds its own tests
  - integration resolves conflicts but does not remove valid coverage

## Conflict Decision Table

| Conflict | Preferred owner | Rule |
|---|---|---|
| Student batch API conflict | `students` | Keep student behavior and field semantics from `students` |
| Approval batch API conflict | `approval` | Keep approval behavior and result semantics from `approval` |
| Message filter conflict | `messages` | Keep filter params, defaults, and invalid-value handling from `messages` |
| Shared service wrapper conflict | `integration` | Collapse duplicates into one more general method |
| Regression test conflict | all | Preserve coverage; merge, do not delete |
| Error message mismatch | `integration` | Normalize to one readable format after merge |

Conflict handling priority:

1. Correct behavior
2. No regression in test coverage
3. Consistent response schema
4. Consistent page behavior
5. Code cleanliness

## Validation Gates

Run at minimum after each backend or integration cherry-pick:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" test
```

Run at minimum after each Flutter-facing cherry-pick:

```bash
dart analyze --no-fatal-warnings
```

Final integration gate:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" test
dart analyze --no-fatal-warnings
```
