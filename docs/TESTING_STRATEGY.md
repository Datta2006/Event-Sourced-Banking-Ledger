# Testing Strategy

## Test Pyramid

```
        /\
       /  \          E2E Tests (docker-compose)
      /____\         ~5% of test suite
     /      \
    /________\       Contract Tests (Spring Cloud Contract / Pact)
   /          \      ~15% of test suite
  /____________\
 /              \   Integration Tests (Testcontainers)
/________________\   ~25% of test suite
 \              /   Unit Tests (JUnit 5 + Mockito)
  \____________/     ~55% of test suite
```

---

## Unit Tests (per service)

**Scope:** Domain logic, event application, saga steps, fraud rules, notification routing.

- **Tools:** JUnit 5 + Mockito
- **Coverage target:** ≥ 80% of domain logic
- **Structure:**
  - Aggregate test: command → events → state transitions
  - Rule test: fraud rule evaluation on sample events
  - Notification test: event → channel mapping

**Example test cases:**
1. `AccountOpened` event applied → account state OPEN
2. `MoneyDeposited` event → balance increases
3. `MoneyWithdrawn` with insufficient funds → `WithdrawalFailed` event emitted
4. Transfer saga: source debited + destination credited → `TransferCompleted`
5. Transfer saga: source insufficient funds → `TransferFailed`
6. Fraud rule: velocity check (5+ transactions in 1 min) → `FraudFlagRaised`
7. Event replay: replay all events → read model matches live state

---

## Integration Tests (Testcontainers)

**Scope:** Real database and broker interaction — no mocks.

- **Tools:** Testcontainers + JUnit 5
- **Containers:** PostgreSQL 16, MongoDB 7, Kafka (Confluent), Redis (optional)
- **Structure:**
  - Spring Boot test slices with `@SpringBootTest`
  - `@AutoConfigureMockMvc` for REST endpoint testing
  - `@EmbeddedKafka` for Kafka consumer/producer tests (alternative to full container)

**Test cases:**
1. Event store append: concurrent writes → optimistic concurrency conflict
2. Kafka producer publishes → Kafka consumer receives
3. Consumer group rebalancing → no lost events
4. MongoDB read model updated after event consumption
5. API Gateway routing to downstream services
6. Docker Compose health checks pass

---

## Contract Tests (Spring Cloud Contract / Pact)

**Scope:** Ensure API/event schema compatibility between services and between versions.

- **Tools:** Spring Cloud Contract (for REST contracts) + Pact (for event contracts)
- **Contract location:** Each service publishes its own contracts; consumers verify against producer contracts in CI.

**Contract files:**
- `account-command-service/src/test/contracts/` — REST contracts for deposit/withdraw/transfer
- `ledger-query-service/src/test/contracts/` — REST contracts for balance/statements queries
- `fraud-detection-service/src/test/contracts/` — Event contracts for `MoneyDeposited`, `TransferCompleted`
- `notification-service/src/test/contracts/` — Event contracts for `FraudFlagRaised`, `TransferCompleted`

**CI enforcement:** Contract tests run on every PR; a breaking change to an event schema fails the build.

---

## End-to-End Tests (Docker Compose)

**Scope:** Full system against real infrastructure via `docker-compose up`.

- **Tools:** REST Assured / Test REST client
- **Test scenarios:**
  1. Open account → check balance → list statements
  2. Deposit → check balance updated → read model converges
  3. Withdraw → check balance → insufficient funds → error
  4. Transfer between accounts → both balances updated → saga completes
  5. Fraud detection: trigger velocity rule → fraud alert raised → notification sent
  6. Event replay: delete read model, replay events, assert read model matches original

---

## Event Replay Correctness Testing

**Goal:** Rebuild a read model from scratch from the event store and assert it matches the live read model.

**Approach:**
1. Capture current read model state (snapshot) before replay
2. Delete read model collections/tables
3. Replay all events from the event store in order
4. Capture new read model state (snapshot) after replay
5. Assert both snapshots are identical (field-by-field comparison)

**Test harness:**
```java
@Test
void eventReplayRebuildsReadModelCorrectly() {
    // 1. Snapshot before
    Map<UUID, Balance> before = ledgerQuery.getAllBalances();

    // 2. Delete read models
    ledgerQuery.deleteAllReadModels();

    // 3. Replay events
    eventReplayService.replayAllEvents();

    // 4. Snapshot after
    Map<UUID, Balance> after = ledgerQuery.getAllBalances();

    // 5. Assert identical
    assertEquals(before, after);
}
```

**Edge cases to test:**
- Event store is empty → read model is empty
- Single event → read model has single entry
- Duplicate events (replay of same stream twice) → read model unchanged (idempotent application)
- Corrupt event (invalid JSON) → replay skips bad event, logs error, continues
- Partial read model rebuild (account subset) → only affected accounts updated
