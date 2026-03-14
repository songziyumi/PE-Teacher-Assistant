# Agent Startup Checklist

Use this checklist before starting work in any teacher P1 worktree.

## 1. Confirm Workspace

- [ ] I am in the correct worktree directory.
- [ ] `git status --short --branch` shows the expected branch.
- [ ] The worktree is clean before I start new edits.
- [ ] I have read `worktree-collaboration-rules.md`.

## 2. Confirm Scope

- [ ] I know which track I own: `students`, `approval`, `messages`, or `integration`.
- [ ] I have the first-batch task statement for my track.
- [ ] I know which files are hard-forbidden for my track.
- [ ] I know which shared files are restricted and which methods I may change.

## 3. Confirm Boundaries

- [ ] I will not edit another track's page files.
- [ ] I will not change shared response fields outside my owned domain.
- [ ] I will not delete existing regression tests from other tracks.
- [ ] If I hit a cross-track conflict, I will note it instead of expanding scope silently.

## 4. Confirm Validation

- [ ] I know the required backend validation command:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" test
```

- [ ] I know the required targeted backend validation when applicable:

```bash
mvn -q "-Dmaven.repo.local=.m2repo" -Dtest=TeacherApiControllerRegressionTest test
```

- [ ] I know the required Flutter validation command:

```bash
dart analyze --no-fatal-warnings
```

## 5. Confirm Commit Discipline

- [ ] I will split work into small commits by backend, Flutter, and fixes when possible.
- [ ] I will use the commit message format `<track>: <change>`.
- [ ] I will avoid `wip`, `fix bug`, or other low-signal commit messages.

## 6. Track-Specific Startup Checks

### `students`

- [ ] I will only change student filtering and batch student operation behavior.
- [ ] I will not modify approval pages or message pages.
- [ ] I will keep student batch results readable and test-covered.

### `approval`

- [ ] I will only change approval batch handling behavior and approval UI flow.
- [ ] I will not modify student list flow or message center filtering flow.
- [ ] I will keep partial-failure handling explicit and test-covered.

### `messages`

- [ ] I will only change teacher message filtering, read-state, and message jump fallback.
- [ ] I will not modify student batch operations or approval batch UI flow.
- [ ] I will keep filter behavior stable for `ALL`, `GENERAL`, and `COURSE_REQUEST`.

### `integration`

- [ ] I will not start by changing core business logic.
- [ ] I will first verify baseline build/test/analyze status.
- [ ] I will use integration only for alignment, cherry-pick intake, and final cleanup.

## 7. Ready To Start

- [ ] Scope is clear.
- [ ] Ownership is clear.
- [ ] Validation is clear.
- [ ] First commit target is clear.
