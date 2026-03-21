# Student Accounts Migration

Goal: move student authentication from `students.password` / `students.enabled`
to `student_accounts`, while keeping the rollout conservative and auditable.

## Files

- `scripts/db/check_student_accounts_migration.sql`
  - Read-only checks for missing accounts, duplicate bindings, duplicate login IDs,
    and remaining legacy password/enabled data.
- `scripts/db/migrate_student_accounts.sql`
  - Backfills `student_accounts` rows for students that do not yet have one.
  - Uses deterministic `login_id = LEGACY + student_id`.
  - Stores legacy password text only in `issued_password` for operator review.
  - Marks migrated accounts as `password_reset_required = 1`.
- `scripts/db/cleanup_student_legacy_fields.sql`
  - Clears `students.password` after accounts have been verified and regenerated.

## Recommended Order

1. Start the upgraded application once so Hibernate can create `student_accounts`
   if the table does not exist yet.
2. Run `scripts/db/check_student_accounts_migration.sql`.
3. Run `scripts/db/migrate_student_accounts.sql`.
4. In the admin UI, batch regenerate or batch reset student accounts so migrated
   placeholder hashes are replaced with real credentials.
5. Run `scripts/db/check_student_accounts_migration.sql` again and confirm:
   - `students_without_account = 0`
   - `duplicate_student_bindings = 0`
   - `duplicate_login_ids = 0`
6. After business verification, run `scripts/db/cleanup_student_legacy_fields.sql`.

## Notes

- The migration SQL does not try to convert old `students.password` values into
  valid password hashes, because historical data may contain mixed plaintext and
  non-current formats.
- The safe path is: backfill rows, then regenerate/reset credentials through the
  application service that already enforces the new account rules.
