# Testing

This project uses tests as executable workflow specifications.

## Unit Tests (Workflow Invariants)

Unit tests focus on the in-memory workflow implementation and enforce domain rules:
- state transitions (publish requires approval, review requires reviewing, etc.)
- stable error codes (`DRAFT_NOT_FOUND`, `INVALID_WORKFLOW_STATE`, `SNAPSHOT_NOT_FOUND`)
- version and snapshot semantics (rollback creates a new published version)

Entry points:
- `src/test/java/com/contentworkflow/workflow/application/ContentWorkflowStateMachineTest.java`
- `src/test/java/com/contentworkflow/workflow/application/ContentWorkflowErrorCodesTest.java`
- `src/test/java/com/contentworkflow/workflow/application/ContentWorkflowHistoryTest.java`

## Persistence Tests (Repository Contract)

`@DataJpaTest` is used to verify the repository layer can persist and query core entities.
By default, Spring Boot will use embedded H2 for these tests.

Entry point:
- `src/test/java/com/contentworkflow/workflow/infrastructure/persistence/PersistenceLayerTest.java`

## Run

```bash
mvn test
```

