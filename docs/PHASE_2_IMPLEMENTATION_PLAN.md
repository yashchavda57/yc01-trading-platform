# Phase 2 Implementation Plan — Core Trading

This is the planning document for Phase 2. Nothing here gets implemented until you've reviewed it
and we've agreed on the approach — same discipline as Phase 1: concept first, then code, one step
at a time, verify before moving on.

Phase 2 delivers the actual trading path: a price feed exists, a user can place an order, the order
gets matched against the book, a trade executes, and money moves. Everything in Phase 1 (auth,
gateway, discovery, config) was *scaffolding*. This is where the system starts doing what a trading
platform actually does.

---

## Architecture Overview

```
market-data-service ──publishes──▶ market.ticks ──▶ (risk-service, later)
      │ (Redis: price:{symbol})

order-service ──publishes (Outbox)──▶ order.placed ──consumed by──▶ matching-engine
      │                                                                    │
      │◀────────────── order.updated (fills/rejects) ─────────────────────┤
                                                                            │
                                                              trade.executed
                                                                     │
                                                      ┌──────────────┴───────────────┐
                                                      ▼                              ▼
                                              wallet-service                portfolio-service (Phase 3)
                                          (settle funds, ledger)
```

**Why this build order (12 → 17):** Kafka has to exist before anything can publish to it. Event
*contracts* (the DTOs flowing over Kafka) have to exist before both a producer and consumer can
agree on a schema. `market-data-service` comes first among the business services because it has no
dependency on anything else — it just needs Kafka + a DB + Redis. `order-service` comes next because
it's the entry point to the trading flow. `matching-engine` needs `order-service` to actually be
producing `order.placed` events to have something to consume. `wallet-service` comes last because it
reacts to both the *start* of an order (fund freeze) and the *end* of a trade (settlement) — it needs
the full event vocabulary to exist first.

---

## Concept — Kafka Cluster Coordination: KRaft vs Zookeeper (know both for interviews)

Every distributed system needs a way to answer: *which broker is the leader for partition X? Which
brokers are currently alive? What's the current partition assignment?* This is **cluster metadata**,
and something has to own it consistently across the whole cluster.

**Zookeeper mode (Kafka's original design, pre-2022):**
Kafka ran alongside a separate distributed coordination service, Apache Zookeeper (also used by
Hadoop, HBase). Zookeeper is a general-purpose distributed key-value store with strong consistency
guarantees (via the ZAB consensus protocol, similar in spirit to Raft/Paxos) — Kafka stored broker
registration, topic configs, ACLs, and controller election state in it. **Why it was a problem**: you
now had two distributed systems to operate, monitor, and scale for one logical cluster. Zookeeper's
consistency model doesn't scale well past a few thousand partitions (metadata propagation gets slow),
which capped how large a Kafka cluster could practically get. It was also an operational burden:
every production Kafka deployment needed a *separate* Zookeeper ensemble (3-5 nodes) with its own
failure modes, tuning, and backup strategy.

**KRaft mode (KIP-500, default since Kafka 3.3+, Zookeeper removed entirely in Kafka 4.0):**
Kafka now manages its own metadata using the **Raft consensus algorithm** internally — a subset of
the Kafka brokers are elected as "controllers" (a quorum) that replicate metadata as a Kafka-style
log, the same replication mechanism Kafka already uses for regular topic data. **Why it's better**:
one system to operate instead of two, metadata propagation is faster (it's an internal log, not a
separate RPC to an external service), and cluster startup/failover is faster since controller
election uses the same Raft-style leader election Kafka's internal architecture already understands.
The tradeoff to be honest about: KRaft is newer, so there's a smaller (though now standard)
production track record compared to Zookeeper mode's decade-plus of battle-testing.

**Interview framing**: "Kafka used to depend on Zookeeper for cluster metadata and controller
election. KIP-500 replaced that with KRaft — Kafka brokers reach quorum on metadata themselves using
Raft, the same way Kafka already replicates partition data, instead of delegating to an external
coordination service. This cut operational complexity roughly in half and improved metadata
propagation latency, which matters for fast leader failover." That one paragraph answers "why did
Kafka remove Zookeeper" cleanly.

We're building on **KRaft**, since it's what any Kafka deployment you touch going forward will use.

---

## Step 12 — Kafka (KRaft mode) in `infrastructure/docker-compose.yml`

**What to do:** Add a single `kafka` service using the `apache/kafka` image (Confluent's official
KRaft-native image works too — `confluentinc/cp-kafka:7.6.0` — pick one, both support `KRAFT` env
vars). Key env vars to configure:
- `KAFKA_PROCESS_ROLES: broker,controller` — this one node plays both roles (fine for local dev;
  production clusters typically separate controller and broker nodes)
- `KAFKA_NODE_ID` and `KAFKA_CONTROLLER_QUORUM_VOTERS` — defines the Raft quorum membership
- `KAFKA_LISTENERS` / `KAFKA_ADVERTISED_LISTENERS` — the classic Kafka networking gotcha: need a
  separate listener for inter-broker/controller traffic vs. what clients (your Spring services)
  connect to. Getting this wrong is the #1 reason "Kafka works from inside Docker but not from my
  host machine" — worth understanding deeply, not just copy-pasting.

Add a healthcheck (`kafka-broker-api-versions --bootstrap-server localhost:9092` or equivalent) so
dependent services in compose can use `depends_on: condition: service_healthy`.

**Verify:** `docker-compose up -d kafka`, container reaches healthy, then from inside the container:
create a test topic, produce a message via `kafka-console-producer`, consume it back via
`kafka-console-consumer` — proves the broker is actually functioning before any Spring service
touches it.

---

## Step 13 — Kafka event contracts in `shared/common-dto`

**Concept:** Every Kafka topic needs an agreed-upon message shape (a **schema**) that both producer
and consumer compile against. We're using plain JSON-serialized POJOs (via `spring-kafka`'s
`JsonSerializer`/`JsonDeserializer`) rather than Avro/Protobuf + Schema Registry — simpler for this
project's scope, but worth knowing the tradeoff: JSON has no schema evolution enforcement (a producer
can silently start sending a field a consumer doesn't expect), whereas Avro + Schema Registry
enforces backward/forward compatibility at write time. Good interview point: "why would a larger
system prefer Avro over JSON on Kafka" — schema evolution safety and smaller wire size are the two
big reasons.

**What to do:** Add to `shared/common-dto/src/main/java/com/chavd/yc01/common/dto/event/`:
- `MarketTickEvent` — symbol, price, volume, timestamp (published to `market.ticks`)
- `OrderPlacedEvent` — orderId, userId, symbol, side (BUY/SELL), orderType, quantity, price,
  timestamp (published to `order.placed`)
- `OrderUpdatedEvent` — orderId, status, filledQuantity, timestamp (published to `order.updated`)
- `TradeExecutedEvent` — tradeId, orderId, buyUserId, sellUserId, symbol, quantity, price, timestamp
  (published to `trade.executed`)

Each is a plain `@Getter @Builder` POJO (matching the `ApiResponse` pattern already in this module) —
no Spring/Kafka annotations belong here, this module stays framework-agnostic like `common-security`.

**Verify:** `mvn compile -pl shared/common-dto` passes; no consumer/producer exists yet to test
against, so this step is verified by compilation + a quick unit test asserting the builder produces
the expected object.

---

## Step 14 — `market-data-service`

**Concept:** This service has three jobs that don't depend on each other much: (1) generate/ingest
price ticks, (2) persist them as time-series data, (3) broadcast the latest price to anyone
listening. We're *simulating* ticks (no real market feed), so a scheduled task generates synthetic
OHLCV data per symbol.

- **TimescaleDB hypertable**: a hypertable is a Postgres table that's automatically partitioned by
  time under the hood (into "chunks"), while still being queried like a normal table. Why it matters:
  a plain Postgres table with billions of tick rows degrades on both writes and time-range queries;
  a hypertable keeps each chunk small enough to index efficiently and lets old chunks be
  compressed/dropped by retention policy without touching the whole table.
- **Continuous aggregates** (1m/5m/1h/1d candles): a materialized view that TimescaleDB
  incrementally refreshes as new ticks land, instead of recomputing OHLCV aggregation from raw ticks
  on every read.
- **WebSocket push (WebFlux)**: clients subscribe to a symbol and get pushed price updates instead of
  polling. Reactive (`Flux<PriceUpdate>`) fits naturally here — many concurrent long-lived
  connections, exactly the same reasoning as the gateway being reactive.
- **Redis cache** (`price:{symbol}`, 5s TTL): the *last* price needs to be readable fast and often
  (every order placement in Phase 2 will want to sanity-check against current price) — cache-aside,
  short TTL because staleness tolerance for a live price is low.

**What to do (package layout):**
```
market-data-service/
├── entity/ (or a Flyway migration only, if using plain JDBC for hypertable inserts)
│   └── db/migration/V1__create_ticks_hypertable.sql
│       (CREATE TABLE ticks(...); SELECT create_hypertable('ticks','ts'); + continuous aggregates)
├── service/
│   ├── TickGeneratorService.java   (@Scheduled, produces synthetic ticks)
│   ├── TickPublisherService.java   (Kafka producer → market.ticks, updates Redis)
│   └── PriceStreamService.java     (Sinks.many().multicast() → Flux for WebSocket subscribers)
├── controller/
│   └── PriceWebSocketHandler.java  (WebFlux, path /ws/prices/{symbol})
└── config/
    └── KafkaProducerConfig.java
```

**Verify:**
1. `mvn compile -pl market-data-service --also-make`
2. Confirm ticks land in the `ticks` hypertable (`SELECT * FROM ticks ORDER BY ts DESC LIMIT 5`)
3. Confirm `market.ticks` topic receives messages (`kafka-console-consumer --topic market.ticks`)
4. Confirm `GET price:{symbol}` in Redis is populated and expiring on schedule
5. Connect a WebSocket test client, confirm live price pushes arrive

---

## Step 15 — `order-service` (full implementation)

**Concept — Order State Machine:** An order moves through defined states:
`PENDING → PLACED → PARTIAL_FILL → FILLED` or `→ CANCELLED` / `→ REJECTED`. Modeling this explicitly
(rather than a free-form status string) means invalid transitions (e.g. `FILLED → PLACED`) are
rejected at the code level, not just by convention. This is **event sourcing lite** — every
transition is itself an auditable fact, useful later for the full trade history audit trail.

**Concept — Idempotency key:** If a client's "place order" request times out and they retry, you do
*not* want two orders placed. Client sends an `Idempotency-Key` header; the service checks Redis for
that key before processing — if seen before, return the cached original response instead of
re-executing. This is the same class of problem payment APIs (Stripe etc.) solve the same way.

**Concept — Outbox Pattern (the most interview-dense part of this step):** The problem: you need to
(a) save the order to Postgres *and* (b) publish `order.placed` to Kafka, and both must happen or
neither must — if the DB commit succeeds but the Kafka publish fails (network blip), the order exists
but the matching engine never hears about it. You can't wrap a Postgres transaction and a Kafka
publish in one atomic operation (they're different systems). The **Outbox pattern** solves this: in
the *same* DB transaction as inserting the order, also insert a row into an `outbox_events` table
(order data as JSON). Both succeed or both roll back — that's a normal single-database transaction, so
it's atomic for free. A separate background poller (or Debezium CDC, out of scope here — polling is
enough) reads unpublished rows from `outbox_events` and publishes them to Kafka, marking them
published after a confirmed send. Worst case on a crash mid-poll: a message gets published twice
(the poller retries), never zero times — Kafka consumers (matching-engine) must therefore be
idempotent too, which is a recurring theme you should be able to name explicitly in an interview:
**at-least-once delivery + idempotent consumers**, not "exactly-once" as a false promise.

**What to do (package layout):**
```
order-service/
├── entity/ Order.java, OutboxEvent.java
├── enums/ OrderStatus.java (state machine transition rules live here or in a dedicated validator)
├── repository/ OrderRepository.java, OutboxEventRepository.java
├── service/
│   ├── OrderService.java          (place/cancel, writes Order + OutboxEvent in one @Transactional)
│   └── OutboxPollerService.java   (@Scheduled, reads unpublished rows, publishes, marks sent)
├── controller/ OrderController.java
└── db/migration/ V1__create_orders_table.sql, V2__create_outbox_events_table.sql
```
Idempotency-key dedup uses Redis, same pattern as the refresh-token store in `user-service`.
Resilience4j circuit breaker wraps the (future) synchronous call path to `matching-engine`, if any
exists outside pure Kafka async flow — worth deciding during implementation whether matching is
purely event-driven (likely, given the architecture) or has a synchronous pre-check.

**Verify:**
1. `POST /api/v1/orders` → row appears in both `orders` and `outbox_events` in the same transaction
2. Within a few seconds, `outbox_events.published = true` and the message appears on `order.placed`
3. Kill Kafka mid-request, place an order, confirm the DB row + outbox row still commit (proving the
   two aren't wrongly coupled), then bring Kafka back and confirm the poller catches up and publishes
4. Repeat the same request with the same `Idempotency-Key` header → confirm only one order exists

---

## Step 16 — `matching-engine`

**Concept — Order Book Data Structure:** Bids (buy orders) need to be sorted **descending** by price
(highest bidder gets matched first), asks (sell orders) **ascending** (lowest seller matched first).
Within the same price, **time priority** (FIFO) applies — first order at that price gets matched
first. `ConcurrentSkipListMap<BigDecimal, PriceLevel>` gives you a thread-safe sorted map in
O(log n) for insert/remove/first-entry — a `PriceLevel` at each price holds a FIFO queue
(`ConcurrentLinkedQueue` or similar) of orders at that price, preserving time priority within a
level.

**Concept — Price-Time Priority Matching:** On a new incoming order, repeatedly peek the best
opposing price level (best bid for an incoming sell, best ask for an incoming buy) — if the prices
cross (bid ≥ ask), match: fill the smaller of the two quantities, partially fill the larger, publish
a `trade.executed` for the matched quantity, and continue until either the incoming order is fully
filled or no more crossing prices exist (remainder rests on the book).

**Concept — Concurrency Strategy:** One order book instance *per instrument* (symbol) — this is a
natural sharding boundary, since orders for AAPL never need to interact with orders for TSLA. Each
instrument's book can use its own `ReentrantReadWriteLock` (reads = viewing book depth, writes =
matching) if you're not going fully lock-free, or you can explore a lock-free design using CAS
operations on the skip list directly for the JMH benchmark stretch goal (>50k matches/sec target).
Start with the `ReentrantReadWriteLock` version — correctness first, then optimize and *measure* with
JMH before claiming a lock-free version is actually faster; that measured-before-claiming discipline
is itself a strong interview signal.

**What to do (package layout, no Spring framework overhead per CLAUDE.md — pure Java + a thin Kafka
consumer/producer wrapper):**
```
matching-engine/
├── orderbook/
│   ├── OrderBook.java        (per-instrument, ConcurrentSkipListMap bids/asks)
│   ├── PriceLevel.java       (FIFO queue at a given price)
│   └── MatchResult.java      (value object: filled qty, remaining qty, trades generated)
├── engine/
│   └── MatchingEngine.java   (routes incoming orders to the right OrderBook by symbol)
├── kafka/
│   ├── OrderPlacedConsumer.java
│   └── TradeExecutedProducer.java
├── snapshot/
│   └── OrderBookSnapshotService.java  (periodic persistence for recovery after restart)
└── benchmark/ (JMH module or separate benchmark source set)
    └── MatchingEngineBenchmark.java
```

**Verify:**
1. Unit tests: exact price match, partial fill, FIFO ordering at same price level, no-match resting
   order
2. Integration: publish two crossing `order.placed` events (a buy and a sell that overlap in price),
   confirm exactly one `trade.executed` appears with correct quantity/price
3. JMH benchmark run, record actual matches/sec — compare against the >50k/sec target, and be ready
   to explain *why* the number is what it is (contention pattern, GC pressure, etc.), not just report it

---

## Step 17 — `wallet-service`

**Concept — Double-Entry Bookkeeping:** Every transaction is recorded as (at least) two ledger
entries: a debit on one account and a matching credit on another, and they must always sum to zero.
This isn't just accounting tradition — it's a correctness invariant you can *query for*: sum of all
entries for a user should always reconcile, and any bug that breaks that (money created/destroyed
from nowhere) is instantly detectable by a reconciliation job, instead of silently corrupting a single
running balance column.

**Concept — Pessimistic Locking (`SELECT FOR UPDATE`)**, contrasted with Phase 1's optimistic
locking on `User`: wallet balance updates are high-contention (many orders touching the same user's
balance in quick succession) and the cost of a failed/retried optimistic-lock transaction on money
movement is worse than the cost of blocking briefly — so here we explicitly take a row lock via
`SELECT ... FOR UPDATE` before debiting/crediting, serializing concurrent updates to the same
account row rather than detecting conflicts after the fact. Interview framing: choose optimistic
locking when conflicts are rare and retry is cheap (Phase 1's `User` updates), pessimistic when
conflicts are frequent and correctness under contention matters more than throughput (money).

**Concept — Fund Freeze/Release (Saga participant):** When `order.placed` is consumed, wallet-service
freezes (holds) the order's estimated value from the user's available balance — not a full debit yet,
since the order might not fill (or might partially fill). On `trade.executed`, the frozen amount is
converted to an actual debit for the filled portion. On `order.updated` with status `CANCELLED` or
`REJECTED`, the freeze is released back to available balance. This is the **choreographed saga**
named in the architecture doc — no central orchestrator, each service reacts to events and emits its
own, and the "transaction" across order-service + wallet-service + matching-engine is the sum of
those reactions rather than a single distributed transaction.

**What to do (package layout):**
```
wallet-service/
├── entity/ Wallet.java, LedgerEntry.java, FundHold.java
├── repository/ WalletRepository.java (custom @Query using SELECT ... FOR UPDATE), LedgerEntryRepository.java
├── service/
│   ├── WalletService.java       (freeze, release, settle — each wraps a pessimistic-locked read)
│   └── LedgerService.java       (writes paired debit/credit entries, enforces the zero-sum invariant)
├── kafka/
│   ├── OrderPlacedListener.java     (freeze funds)
│   ├── OrderUpdatedListener.java    (release on cancel/reject)
│   └── TradeExecutedListener.java   (settle: freeze → actual debit/credit)
└── db/migration/ V1__create_wallets_table.sql, V2__create_ledger_entries_table.sql, V3__create_fund_holds_table.sql
```
Idempotent transaction IDs: each Kafka event has a unique ID; wallet-service records processed event
IDs (or relies on a unique constraint on `(event_id, entry_type)` in `ledger_entries`) so a
redelivered Kafka message (at-least-once delivery, same theme as Step 15's Outbox) doesn't double-debit.

**Verify:**
1. Place an order → confirm a `FundHold` row appears and available balance decreases by the held amount
2. Simulate a `trade.executed` → confirm the hold converts to a real ledger debit/credit pair, sums to zero
3. Cancel an unfilled order → confirm the hold releases and available balance is restored
4. Replay the same `trade.executed` message twice manually → confirm the ledger does **not** double-settle

---

## Progress Tracker (Phase 2)

| Step | Description | Status |
|---|---|---|
| 12 | Kafka (KRaft mode) in `docker-compose.yml` | ⬜ |
| 13 | Kafka event contracts in `shared/common-dto` | ⬜ |
| 14 | `market-data-service` | ⬜ |
| 15 | `order-service` (full implementation) | ⬜ |
| 16 | `matching-engine` | ⬜ |
| 17 | `wallet-service` | ⬜ |

---

## Open Questions to Resolve Before/During Implementation

1. **`order-service` ↔ `matching-engine` interaction**: purely Kafka-async (order-service fires
   `order.placed` and moves on, `order.updated` comes back later), or does `order-service` also need
   a synchronous pre-check (e.g. instrument exists, market is open) before even writing the order?
   This affects whether Resilience4j circuit breaker is needed here at all in Phase 2, or is more
   relevant once `risk-service` exists in Phase 3.
2. **TimescaleDB deployment**: same Postgres container as `trading_users`/`trading_users` DB with the
   TimescaleDB extension enabled, or a separate container? A separate container is more realistic to
   production (isolates time-series workload from OLTP) and avoids extension-conflict risk on the
   existing `trading-postgres` container.
3. **Order book snapshot recovery** (Step 16): how often, and what does startup recovery actually look
   like — replay from snapshot + replay any `order.placed` events since the snapshot from Kafka
   (using consumer offset), or snapshot-only (accepting some data loss on crash)? Worth deciding
   explicitly rather than leaving implicit, since "how do you recover in-memory state after a crash"
   is a very likely interview question given this design choice.
