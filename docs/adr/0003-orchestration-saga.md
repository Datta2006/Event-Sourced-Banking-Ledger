# ADR 0003: Orchestration-based Saga vs Choreography

## Status
Accepted

## Context
Money transfers between accounts require cross-service consistency. We need to choose between orchestration (centralized coordinator) and choreography (event-driven, no central coordinator).

## Decision
Use **orchestration** inside the Account Command Service via an explicit `SagaOrchestrator` component.

## Consequences

### Positive
- Clear step-by-step visibility into the transfer flow
- Centralised compensating logic — easier to test and debug
- Simpler to reason about for a course project (grading focus)
- Explicit state machine makes failure scenarios traceable

### Negative
- Orchestrator is a potential single point of failure / bottleneck
- Adds coupling between orchestrator and participant services (though we only have one service here)

## Mitigation
- Idempotent steps with optimistic concurrency on saga state
- Saga state persisted in same PostgreSQL transaction as events
- Background recovery job picks up incomplete sagas on restart

## Alternatives Considered
- **Choreography:** Services emit events and react to each other. More decoupled but harder to trace, debug, and demonstrate for grading.