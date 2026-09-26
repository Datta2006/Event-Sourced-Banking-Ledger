# Feature 07: Event Replay & Disaster Recovery

## User Story

As an operator, I want to rebuild read models from the event store so that I can recover from data corruption or schema changes.

## Acceptance Criteria

- [ ] Admin endpoint POST `/api/admin/replay` triggers full event replay
- [ ] Replay reads all events from event store in order
- [ ] Replay applies events to read models idempotently
- [ ] Replay can be scoped: single account, date range, event type
- [ ] Progress tracked: events processed, accounts updated, errors
- [ ] After replay, read models match event store exactly (verified by test)
- [ ] Read model snapshotting (stretch): store balance at version N for faster replay

## Services / Events / Endpoints Touched

| Component | Detail |
|---|---|
| API | POST `/api/admin/replay` |
| Ledger Query | Replay logic, idempotent event application |
| Event Store | Read all events |
| Kafka | Not involved (direct event store read) |

## Edge Cases

- Replay on live system → concurrent events during replay handled correctly
- Corrupt event (invalid JSON) → skip, log, continue
- Duplicate events during replay → idempotent apply (no double-count)
- Large event store (>1M events) → batch processing, progress reporting
- Replay triggered during transfer saga → saga state rebuilt correctly

## Definition of Done

- [ ] Unit tests: idempotent event application
- [ ] Integration test: replay rebuilds read model from scratch
- [ ] Load test: replay 10k events, verify performance
- [ ] Correctness test: replay result matches live (see TESTING_STRATEGY.md)
- [ ] OpenAPI spec updated
- [ ] Docs updated
- [ ] Manual: delete read model collections, trigger replay, verify restored