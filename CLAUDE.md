# CLAUDE.md — Trading Platform (`com.chavd.yc01.trading-platform`)

This file is the master context document for Claude Code sessions on this project.

## Project Goal

Build a production-grade stock trading platform (think Zerodha / Wealthsimple / Robinhood) in Java Spring Boot as a single portfolio artifact for senior backend developer interviews. The goal is to cover every topic that appears in senior backend interviews — concurrency, distributed systems, caching, messaging, Kubernetes, observability, and performance. Frontend is intentionally minimal.

**Stack:**
- Java 21 + Spring Boot 4.x (microservices, Maven multi-module)
- API Gateway: Spring Cloud Gateway
- Service Discovery: Spring Cloud Eureka
- Config: Spring Cloud Config Server
- Messaging: Apache Kafka
- Cache: Redis (cluster mode) + Caffeine (L1 in-process)
- DB: PostgreSQL (OLTP) + TimescaleDB (time-series market data) + Elasticsearch (search/audit)
- Containers: Docker + Kubernetes (Helm chart)
- Observability: Prometheus + Grafana + ELK Stack + Zipkin (distributed tracing)
- Testing: JUnit 5 + Mockito + Testcontainers + Gatling (load)
- CI/CD: GitHub Actions

---

## High-Level Architecture

```
                    ┌─────────────────────────────────────────┐
                    │        API GATEWAY (Spring Cloud)        │
                    │  Rate Limiting · Auth Filter · Routing   │
                    └──────────────────┬──────────────────────┘
                                       │
       ┌───────────────────────────────┼───────────────────────────────┐
       │                              │                                │
┌──────▼──────┐           ┌───────────▼──────────┐        ┌───────────▼────────┐
│ User Service│           │ Market Data Service   │        │   Order Service    │
│ (Auth/KYC)  │           │ (Prices / OHLCV)     │        │ (Place/Cancel)     │
└─────────────┘           └───────────────────────┘        └────────────────────┘
       │                              │                                │
       └────────────────── Kafka Cluster ─────────────────────────────┘
                                       │
       ┌───────────────────────────────┼───────────────────────────────┐
       │                              │                                │
┌──────▼──────┐           ┌───────────▼──────────┐        ┌───────────▼────────┐
│  Matching   │           │  Portfolio Service    │        │  Wallet Service    │
│  Engine     │           │  (Holdings / P&L)     │        │  (Funds / Ledger)  │
└─────────────┘           └───────────────────────┘        └────────────────────┘
       │                              │                                │
┌──────▼──────┐           ┌───────────▼──────────┐        ┌───────────▼────────┐
│ Risk Service│           │ Notification Service  │        │  Report Service    │
│ (Limits)    │           │ (Email/SMS/Push)       │        │ (Tax/Statements)   │
└─────────────┘           └───────────────────────┘        └────────────────────┘
```

---

## Microservices

### 1. `api-gateway`
Spring Cloud Gateway. Responsibilities:
- JWT validation filter (RS256)
- Redis token-bucket rate limiting per user/IP
- Path routing to downstream services
- Circuit breaker (Resilience4j) at the edge
- Request correlation ID injection for distributed tracing

### 2. `user-service`
- Registration, email verification, OTP-based 2FA
- JWT access token (15 min) + refresh token (7 days) stored in Redis
- Role-based access control: USER, ADMIN, BROKER
- KYC document upload (MinIO/S3), status state machine: PENDING → VERIFIED → REJECTED
- OAuth2 SSO (Google/GitHub) via Spring Security

### 3. `market-data-service`
- Ingests simulated OHLCV tick data; publishes to Kafka topic `market.ticks`
- Stores ticks in TimescaleDB (hypertables, continuous aggregates for candles: 1m/5m/1h/1d)
- Pushes live prices to clients via WebSocket (Spring WebFlux)
- Last price cached in Redis: key `price:{symbol}`, TTL 5 s

### 4. `order-service`
- Order types: Market, Limit, Stop-Loss, Stop-Limit, GTC
- Order state machine: PENDING → PLACED → PARTIAL_FILL → FILLED / CANCELLED / REJECTED
- Idempotency key on order creation (Redis-based dedup)
- Publishes `order.placed` to Kafka via **Outbox pattern** (guaranteed delivery)
- Circuit breaker (Resilience4j) on calls to matching engine

### 5. `matching-engine` ← most complex service
- Pure Java, no framework overhead
- Order book: `ConcurrentSkipListMap` per instrument (bids descending, asks ascending)
- Price-Time Priority (FIFO) matching; partial fill support
- Lock-free or fine-grained `ReentrantReadWriteLock` per instrument
- Publishes `trade.executed` events to Kafka
- Periodic order-book snapshot persisted to DB for recovery
- JMH benchmarks to measure throughput (target: >50k matches/sec single node)

### 6. `portfolio-service`
- Consumes `trade.executed` → updates holdings
- Calculates realized/unrealized P&L, XIRR, average cost basis
- **CQRS**: write side event-driven, read side materialized view in Redis
- Multi-level cache: Caffeine (L1, 30 s) → Redis (L2, 5 min)

### 7. `wallet-service`
- Double-entry bookkeeping ledger (every debit has a matching credit)
- Pessimistic locking: `SELECT FOR UPDATE` on balance row
- Fund freeze on order placement; release on cancel / expiry
- Idempotent transaction IDs

### 8. `risk-service`
- Pre-trade risk checks: position limits, order value cap, margin check
- **Kafka Streams** tumbling window: halt trading on an instrument if price moves >10% in 5 min
- Real-time margin calculation

### 9. `notification-service`
- Kafka consumer for all event topics
- Dispatches email (JavaMail + Thymeleaf templates), SMS (Twilio stub), push (Firebase FCM)
- Retry with exponential backoff; failed messages → Dead Letter Queue (DLQ)

### 10. `report-service`
- Spring Batch for nightly report generation (P&L, tax statement, PDF)
- Elasticsearch for trade history full-text search
- CSV / PDF export

---

## Kafka Topics

| Topic | Producer | Consumers | Partition Key |
|---|---|---|---|
| `market.ticks` | market-data-service | market-data, risk | symbol |
| `order.placed` | order-service | matching-engine | symbol |
| `trade.executed` | matching-engine | portfolio, wallet, notification | user_id |
| `order.updated` | matching-engine | order-service, notification | order_id |
| `notification.events` | all services | notification-service | user_id |

- Exactly-once semantics (Kafka transactions) for trade settlement
- Dead Letter Queue per topic for unprocessable messages

---

## Database Strategy

| Store | Used For |
|---|---|
| PostgreSQL | Users, orders, wallet ledger, positions |
| TimescaleDB | OHLCV tick data, candlestick aggregates |
| Redis | Cache, sessions, rate limiting, real-time prices, order-book snapshots |
| Elasticsearch | Trade history search, audit logs |

Key patterns:
- Flyway for DB migrations (versioned + repeatable)
- HikariCP connection pool tuning
- Read replicas for portfolio/report read queries
- Shard `orders` table by `user_id % N` for horizontal scale

---

## Key Distributed Systems Patterns Implemented

| Pattern | Where |
|---|---|
| Saga (Choreography) | Order placement → fund freeze → match → settle |
| Outbox | order-service reliable Kafka publishing |
| Idempotency | Order creation, payment processing |
| Circuit Breaker | Gateway and order-service (Resilience4j) |
| Bulkhead | Thread pool isolation per downstream dependency |
| Retry + Exponential Backoff | notification-service |
| Dead Letter Queue | All Kafka consumer services |
| CQRS | portfolio-service |
| Event Sourcing | Order state transitions (full audit trail) |
| Cache-Aside / Write-Through | portfolio-service, price cache |

---

## Observability

- **Structured logging**: SLF4J + Logback JSON → ELK (Elasticsearch, Logstash, Kibana)
- **Distributed tracing**: Micrometer + Zipkin — trace full order lifecycle across services via `X-Correlation-ID`
- **Metrics**: Micrometer → Prometheus → Grafana
  - JVM: GC pause, heap, threads
  - Business: orders/sec, match latency p99, active WebSocket connections, Kafka consumer lag
- **Health**: Spring Actuator `/health`, liveness/readiness probes for K8s
- **Alerting**: Prometheus AlertManager (e.g. consumer lag > 1000 → alert)

---

## Project Structure (Target)

```
yc01-trading-platform/         ← this repo (currently single-module, evolving to multi-module)
├── gateway-service/
├── user-service/
├── market-data-service/
├── order-service/
├── matching-engine/
├── portfolio-service/
├── wallet-service/
├── risk-service/
├── notification-service/
├── report-service/
├── config-server/
├── service-discovery/
├── shared/
│   ├── common-dto/            (Kafka event POJOs shared across services)
│   ├── common-exceptions/
│   └── common-security/       (JWT util reused by all services)
├── infrastructure/
│   ├── docker-compose.yml
│   ├── k8s/helm/
│   ├── grafana/dashboards/
│   └── prometheus/
└── docs/
    ├── HLD.md
    ├── LLD/
    └── ADRs/                  (Architecture Decision Records)
```

---

## Implementation Phases

| Phase | Focus | Weeks |
|---|---|---|
| 1 | Foundation: user-service, gateway, docker-compose, config | 1–3 |
| 2 | Core trading: market-data, order, matching-engine, wallet | 4–7 |
| 3 | Business logic: portfolio (CQRS), risk (Kafka Streams), notification | 8–10 |
| 4 | Operational: report (Spring Batch), tracing, Grafana, K8s Helm | 11–13 |
| 5 | Performance: JMH benchmarks, Gatling load tests, DB tuning | 14–16 |

---

## Interview Coverage

| Interview Topic | Point To |
|---|---|
| Java concurrency | Matching engine: lock-free order book, ReentrantReadWriteLock, JMH |
| Spring Boot internals | Any service: autoconfiguration, AOP for logging/timing, bean lifecycle |
| Database design | Wallet double-entry, TimescaleDB partitioning, shard strategy |
| Caching | Portfolio multi-level (Caffeine + Redis), price cache, session store |
| Kafka / messaging | Order lifecycle events, Kafka Streams in risk-service, exactly-once |
| Microservices patterns | Saga, Outbox, CQRS, Circuit Breaker, Bulkhead across all services |
| REST API design | Versioning, idempotency keys, cursor-based pagination |
| Security | JWT RS256, OAuth2, RBAC, rate limiting, OWASP |
| Docker / Kubernetes | Full Helm chart, HPA, liveness/readiness probes |
| Observability | Prometheus/Grafana/ELK/Zipkin fully wired |
| Performance | JMH benchmarks, Gatling load tests, GC tuning, HikariCP |
| Testing | Unit (JUnit5/Mockito), Integration (Testcontainers), Load (Gatling) |
| System design | Whiteboard the full HLD — you built it |
