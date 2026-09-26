# ADR 0002: Apache Kafka as Message Broker

## Status
Accepted

## Context
We need an async event bus between microservices. Options: Apache Kafka, RabbitMQ, or Redpanda.

## Decision
Use Apache Kafka (Confluent Platform image) as the event broker.

## Consequences

### Positive
- Native event log with built-in replay capability — matches our disaster recovery story
- Strong ordering guarantees per partition (maps to aggregate-versioned events)
- Wide ecosystem (Debezium CDC, Schema Registry as stretch goal)
- Every reference CQRS banking demo we reviewed uses Kafka

### Negative
- Higher operational complexity than RabbitMQ (Zookeeper in our setup)
- More memory/CPU overhead per container
- Learning curve for team members unfamiliar with Kafka

## Alternatives Considered
- **RabbitMQ:** Simpler AMQP model, but lacks native event log replay — complicates read-model rebuild
- **Redpanda:** Kafka-compatible, lighter footprint — viable local-dev alternative if Kafka is too heavy