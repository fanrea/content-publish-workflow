# PRD

## Problem Statement
Most training-style projects stop at simple content CRUD and "publish/unpublish". Real content platforms need stronger workflow guarantees:
- a draft cannot be published without review
- operators need review comments and audit history
- every published version must be traceable
- bad releases must be rolled back quickly
- publish side effects should be explicit and retryable

This project solves that gap by building a dedicated content publish workflow platform.

## Target Users
- Content editor: creates and updates drafts
- Reviewer: approves or rejects content
- Operator: triggers publish, offline, rollback
- Auditor: checks review and publish history

## Core Business Scenarios
### Scenario A: Normal release
1. Editor creates draft
2. Editor updates content
3. Editor submits review
4. Reviewer approves
5. Operator publishes
6. System creates version snapshot and publish tasks

### Scenario B: Rejected content
1. Draft enters review
2. Reviewer rejects with comment
3. Editor modifies draft
4. Editor resubmits review

### Scenario C: Bad release rollback
1. Version N is already published
2. Operator detects content issue
3. Operator selects version N-1
4. System creates rollback release based on previous snapshot

## Scope
### In Scope
- draft management
- workflow status management
- review submission and decision
- publish action
- publish snapshot
- rollback based on snapshot
- review history query
- snapshot history query
- publish task model

### Out of Scope For V1
- role-based permission system
- search index integration
- MQ integration
- asynchronous worker execution
- full MySQL persistence implementation
- frontend UI
