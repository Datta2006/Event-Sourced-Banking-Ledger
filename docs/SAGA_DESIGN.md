# Saga Design — Money Transfer

## Overview

The money transfer between two accounts is the primary cross-service consistency scenario. We use **orchestration-based saga** inside the Account Command Service.

## Transfer Saga Steps

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant GW as API Gateway
    participant CMD as Account Command Service
    participant PG as PostgreSQL (Event Store)
    participant KAF as Kafka
    participant LED as Ledger Query
    participant FRD as Fraud Detection
    participant NTF as Notification

    Client->>GW: POST /accounts/transfer {source, dest, amount}
    GW->>CMD: POST /accounts/transfer
    CMD->>PG: Begin transaction
    CMD->>PG: Validate source account exists & open
    CMD->>PG: Validate dest account exists & open
    CMD->>PG: Check sufficient balance
    CMD->>PG: Reserve funds (debit pending)
    CMD->>PG: Write TransferInitiated event (version N)
    CMD->>PG: Commit
    CMD->>KAF: Publish TransferInitiated
    KAF-->>LED: Consume TransferInitiated
    KAF-->>FRD: Consume TransferInitiated
    KAF-->>NTF: Consume TransferInitiated
    CMD-->>GW: 200 {transferId, status: INITIATED}
    GW-->>Client: 200 {transferId}

    rect rgb(240, 248, 255)
        note right of FRD: Async fraud check
        FRD->>FRD: Apply velocity/threshold/geo rules
        alt Fraud detected
            FRD->>KAF: Publish FraudFlagRaised
            KAF-->>NTF: Consume FraudFlagRaised
            NTF->>NTF: Send alert email
            FRD->>KAF: Publish TransferCompensated (after orchestrator reacts)
        end
    end

    rect rgb(255, 248, 240)
        note right of CMD: Orchestrator continues
        CMD->>CMD: Saga state = SOURCE_DEBITED
        CMD->>PG: Begin transaction
        CMD->>PG: Credit destination account
        CMD->>PG: Write MoneyDeposited (dest) event
        CMD->>PG: Write TransferCompleted event
        CMD->>PG: Commit
        CMD->>KAF: Publish MoneyDeposited, TransferCompleted
        KAF-->>LED: Consume events
        KAF-->>NTF: Consume TransferCompleted
        NTF->>NTF: Send confirmation emails
    end
```

## Saga State Machine

```mermaid
stateDiagram-v2
    [*] --> INITIATED : TransferInitiated
    INITIATED --> SOURCE_DEBITED : Reserve funds OK
    INITIATED --> COMPENSATING : Insufficient funds / account closed
    SOURCE_DEBITED --> DESTINATION_CREDITED : Credit dest OK
    SOURCE_DEBITED --> COMPENSATING : Dest account closed / timeout
    DESTINATION_CREDITED --> COMPLETED : TransferCompleted
    COMPENSATING --> ROLLED_BACK : Compensation done
    COMPLETED --> [*]
    ROLLED_BACK --> [*]

    note right of COMPENSATING
        Compensation actions:
        - If SOURCE_DEBITED failed before dest credit: no action needed (pending debit not committed)
        - If DEST_CREDITED: write TransferCompensated, reverse dest credit
    end note
```

## Saga State Persistence

The saga state is persisted in the **same PostgreSQL transaction** as the events:

```sql
CREATE TABLE saga_transfers (
    transfer_id     UUID PRIMARY KEY,
    source_account  UUID NOT NULL,
    dest_account    UUID NOT NULL,
    amount          NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    state           VARCHAR(20) NOT NULL,
    current_step    INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0  -- optimistic locking
);
```

**Optimistic concurrency:** Each saga state update increments `version` and checks expected version — prevents double-processing on retries.

## Compensating Actions

| Failure Point | Compensation |
|---|---|
| Source account closed / insufficient funds before reserve | No compensation needed — `TransferFailed` event emitted |
| Destination account closed after source reserved | Emit `TransferCompensated`, reverse pending debit on source |
| Fraud flag raised after source reserved | Emit `TransferCompensated`, reverse pending debit, alert |
| Destination credit fails (DB error) | Retry with exponential backoff; after max retries, compensate |
| Timeout waiting for async step | Saga orchestrator timeout handler triggers compensation |

## Timeout & Retry Handling

- **Step timeout**: 30 seconds per step (configurable).
- **Retry policy**: Exponential backoff (1s, 2s, 4s, 8s) — max 3 retries per step.
- **Dead letter**: After max retries, saga moves to `COMPENSATING` state and alerts operations.
- **Idempotency**: All saga steps are idempotent — repeating a step with same `transferId` and `currentStep` is a no-op.

## Event Replay & Recovery

On service restart, the Account Command Service can rebuild saga states by reading all `TransferInitiated` events and replaying the state machine. Incomplete sagas (state ≠ COMPLETED/ROLLED_BACK) are picked up by a background recovery job that continues from the last persisted step.