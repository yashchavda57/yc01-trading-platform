# Trading Platform

A production-grade stock trading platform (Zerodha / Wealthsimple / Robinhood
style) built in Java + Spring Boot as a portfolio project covering the
concurrency, distributed-systems, and infrastructure topics that come up in
senior backend interviews. Full scope and architecture rationale live in
[`CLAUDE.md`](CLAUDE.md); this file tracks what's actually built.

## Current status

**Phase 2 (Core Trading), Step 16 of 17.** See
[`dev-journal/CHECKPOINT.md`](dev-journal/CHECKPOINT.md) for the authoritative,
continuously-updated step-by-step status, and
[`dev-journal/INDEX.md`](dev-journal/INDEX.md) for a write-up of every step
landed so far (what was built, why, and the concepts it demonstrates).

Built and verified:
- `service-discovery` (Eureka), `config-server` (centralized config)
- `api-gateway` — routing + JWT auth filter (routes to `user-service` only so far)
- `user-service` — registration, login, refresh/logout, JWT (RS256)
- `market-data-service` — simulated tick generation, TimescaleDB hypertable +
  continuous-aggregate candles, Kafka producer, Redis price cache, WebSocket
  live price streaming
- `order-service` — order state machine, idempotent order placement, the
  outbox pattern (verified to survive a Kafka outage)
- `matching-engine` — pure-Java (no framework) order book with price-time
  priority matching, per-instrument locking, JMH-measured throughput
  (~790k-1.24M ops/sec, see [Step 16](dev-journal/steps/step16-matching-engine.md))

Not yet built: `wallet-service`, `portfolio-service`, `risk-service`,
`notification-service`, `report-service`, Kubernetes/Helm, observability
stack (Prometheus/Grafana/ELK/Zipkin), JMH/Gatling load testing beyond the
matching-engine benchmark above.

## Diagrams

[`diagrams/`](diagrams/) has the architecture as two comparable pictures:
- [`diagrams/target-state/`](diagrams/target-state/) — the finished system
- [`diagrams/current-state/`](diagrams/current-state/) — what's real today

Both are Mermaid, rendered natively by GitHub. Start with
[`diagrams/current-state/architecture.md`](diagrams/current-state/architecture.md)
for the fastest overview of what actually runs right now.

## Stack

Java 21 · Spring Boot 4 · Spring Cloud (Gateway, Eureka, Config) · Apache
Kafka · Redis · PostgreSQL · TimescaleDB · Docker Compose. Full stack and
target tooling (Kubernetes, Elasticsearch, Prometheus/Grafana/ELK/Zipkin,
Gatling) in [`CLAUDE.md`](CLAUDE.md).

## Running locally

```
cd infrastructure
docker compose up -d postgres timescaledb redis kafka
```

Then start `service-discovery` → `config-server` → the individual services
(each is a standard Spring Boot app, `matching-engine` is a plain runnable
jar: `java -jar matching-engine/target/matching-engine.jar`).
