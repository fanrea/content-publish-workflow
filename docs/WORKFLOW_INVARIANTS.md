# Workflow Invariants

This project is intentionally modeled as a workflow system. The important part is not the tech stack,
but the invariants we enforce under real-world usage (retries, bad releases, audits).

## State Machine Rules (V1)

Allowed transitions:
- `DRAFT` -> `REVIEWING` via submit-review
- `REVIEWING` -> `APPROVED` via approve
- `REVIEWING` -> `REJECTED` via reject (sets `lastReviewComment`)
- `REJECTED` -> `DRAFT` via edit (increments `draftVersion`)
- `APPROVED` -> `PUBLISHED` via publish (creates snapshot + tasks)
- `PUBLISHED` -> `PUBLISHED` via rollback (creates a new snapshot version)

Notable restrictions:
- You cannot publish without approval.
- You cannot review content that is not in `REVIEWING`.
- Editing is only allowed when the content is not under review (implementation allows `DRAFT`, `REJECTED`, `OFFLINE`).

## Versioning Semantics

- `draftVersion` is for editor-side evolution. It increments on each edit.
- `publishedVersion` is for operator-side releases. It increments on each publish or rollback.
- A rollback does not mutate history. It creates a new published version whose content is copied from a previous snapshot.

## Snapshot Guarantees

- Each `(draftId, publishedVersion)` has exactly one snapshot.
- Snapshots are immutable and represent the released content at that version.
- `sourceDraftVersion` enables tracing: "which draft version produced this release".

## Publish Task Guarantees (Planned)

Publish is separated into core data changes and side-effect tasks.
The schema supports idempotent task creation by key `(draftId, publishedVersion, taskType)`.

## Error Codes (Current)

The in-memory implementation uses `BusinessException(code, message)`:
- `DRAFT_NOT_FOUND`: draft id does not exist
- `INVALID_WORKFLOW_STATE`: action not allowed in current state
- `SNAPSHOT_NOT_FOUND`: rollback target version missing

These codes are intentionally stable to support API clients.

