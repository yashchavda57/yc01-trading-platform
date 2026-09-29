# Current-State Architecture

What's actually built and verified as of **Step 16** (see `dev-journal/CHECKPOINT.md`
for the authoritative step-by-step status). Compare against
`../target-state/architecture.md` for the gap.

```mermaid
flowchart TB
    Client(["Client"])

    subgraph Edge["Edge"]
        Gateway["api-gateway<br/>routing + JWT filter<br/>(no rate limit / circuit breaker yet)"]
    end

    subgraph Platform["Platform"]
        Eureka["service-discovery<br/>(Eureka)"]
        Config["config-server<br/>(native config-repo)"]
    end

    subgraph Built["Built Services"]
        User["user-service<br/>register/login/refresh/logout, JWT RS256"]
        MarketData["market-data-service<br/>tick sim · WebSocket · candles"]
        Order["order-service<br/>state machine · idempotency · outbox"]
        Matching["matching-engine<br/>pure Java · order book · JMH-measured"]
    end

    subgraph NotBuilt["Not Built Yet (Phase 3-5)"]
        Portfolio["portfolio-service"]
        Wallet["wallet-service"]
        Risk["risk-service"]
        Notification["notification-service"]
        Report["report-service"]
    end

    Kafka{{"Kafka (KRaft, single node)"}}

    subgraph Stores["Data Stores (all running locally via docker-compose)"]
        PgUsers[("Postgres:<br/>trading_users")]
        PgOrders[("Postgres:<br/>trading_orders")]
        PgMatching[("Postgres:<br/>trading_matching_engine<br/>(order book snapshots)")]
        Timescale[("TimescaleDB:<br/>trading_market_data")]
        Redis[("Redis:<br/>sessions · price cache ·<br/>idempotency keys")]
    end

    Client --> Gateway
    Gateway --> User
    Gateway -.not yet routed.-> MarketData
    Gateway -.not yet routed.-> Order

    Built -.registers with.-> Eureka
    Built -.pulls config from.-> Config

    User --> PgUsers
    User --> Redis
    Order --> PgOrders
    Order --> Redis
    Matching --> PgMatching
    MarketData --> Timescale
    MarketData --> Redis

    MarketData -- market.ticks --> Kafka
    Order -- "order.placed (via outbox)" --> Kafka
    Kafka -- order.placed --> Matching
    Matching -- trade.executed --> Kafka
    Matching -.nothing consumes yet.-> NotBuilt

    style NotBuilt stroke-dasharray: 5 5
```

## What's real vs. simulated right now

- **market.ticks** are synthetic (random walk), not a real market feed — by
  design, per CLAUDE.md.
- **trade.executed** is published by matching-engine but **nothing consumes
  it yet** — portfolio-service and wallet-service (Step 17+) are the intended
  consumers.
- **matching-engine has no idempotent-consumer dedup** on replayed
  `order.placed` events yet — a known gap, documented in
  `dev-journal/steps/step16-matching-engine.md`.
- **api-gateway routes only to user-service today** — market-data-service and
  order-service aren't yet reachable through the gateway (verified directly
  against their own ports in dev-journal steps 14/15).
