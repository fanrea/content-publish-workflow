# Domain Model

## Aggregate Root
### ContentDraft
Represents the editable and workflow-controlled content object.

Key fields:
- `id`
- `bizNo`
- `title`
- `summary`
- `body`
- `draftVersion`
- `publishedVersion`
- `status`
- `currentSnapshotId`
- `lastReviewComment`

## Supporting Entities
### ReviewRecord
Tracks each review action for a draft.

### ContentSnapshot
Stores immutable published content for a specific version.

### PublishTask
Represents side-effect tasks produced by a publish action.

### PublishLog
High-level audit log for operator actions (submit/review/publish/rollback/offline).

## Workflow States
- `DRAFT`
- `REVIEWING`
- `REJECTED`
- `APPROVED`
- `PUBLISHING`
- `PUBLISHED`
- `PUBLISH_FAILED`
- `OFFLINE`
