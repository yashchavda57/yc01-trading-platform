# Current-State Architecture

What's actually built and verified as of **Step 18** (see
`dev-journal/CHECKPOINT.md` for the authoritative step-by-step status).
Compare against `../target-state/architecture.md` for the gap.

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
        Wallet["wallet-service<br/>double-entry ledger · pessimistic locking · fund holds"]
        Portfolio["portfolio-service<br/>CQRS · weighted-avg cost basis · realized P&L"]
    end

    subgraph NotBuilt["Not Built Yet (Phase 3-5)"]
        Risk["risk-service"]
        Notification["notification-service"]
        Report["report-service"]
    end

    Kafka{{"Kafka (KRaft, single node)"}}

    subgraph Stores["Data Stores (all running locally via docker-compose)"]
        PgUsers[("Postgres:<br/>trading_users")]
        PgOrders[("Postgres:<br/>trading_orders")]
        PgMatching[("Postgres:<br/>trading_matching_engine<br/>(order book snapshots)")]
        PgWallets[("Postgres:<br/>trading_wallets")]
        PgPortfolios[("Postgres:<br/>trading_portfolios")]
        Timescale[("TimescaleDB:<br/>trading_market_data")]
        Redis[("Redis:<br/>sessions · price cache ·<br/>idempotency keys ·<br/>portfolio holdings cache")]
    end

    Client --> Gateway
    Gateway --> User
    Gateway -.not yet routed.-> MarketData
    Gateway -.not yet routed.-> Order
    Gateway -.not yet routed.-> Wallet

    Built -.registers with.-> Eureka
    Built -.pulls config from.-> Config

    User --> PgUsers
    User --> Redis
    Order --> PgOrders
    Order --> Redis
    Matching --> PgMatching
    MarketData --> Timescale
    MarketData --> Redis
    Wallet --> PgWallets
    Wallet --> Redis
    Portfolio --> PgPortfolios
    Portfolio --> Redis

    MarketData -- market.ticks --> Kafka
    Order -- "order.placed (via outbox)" --> Kafka
    Kafka -- order.placed --> Matching
    Kafka -- order.placed --> Wallet
    Matching -- trade.executed --> Kafka
    Kafka -- trade.executed --> Wallet
    Kafka -- trade.executed --> Portfolio
    Kafka -.order.updated: no producer yet.-> Wallet

    style NotBuilt stroke-dasharray: 5 5
```

## What's real vs. simulated right now

- **market.ticks** are synthetic (random walk), not a real market feed — by
  design, per CLAUDE.md.
- **trade.executed** is consumed by `wallet-service` (settles the trade) and
  by `portfolio-service` (updates holdings + realized P&L) — both verified
  live, including idempotent replay and the two-sided-trade locking (see
  `dev-journal/steps/step18-portfolio-service.md`).
- **`order.updated` has no producer anywhere in the system.** wallet-service's
  `OrderUpdatedListener` (release-hold-on-cancel) is contract-correct and was
  verified by hand-publishing test messages directly to the topic — not via a
  real end-to-end flow, since neither `matching-engine` nor `order-service`
  emits this event yet. See `dev-journal/steps/step17-wallet-service.md`.
- **matching-engine has no idempotent-consumer dedup** on replayed
  `order.placed` events yet — a known gap, documented in
  `dev-journal/steps/step16-matching-engine.md`.
- **`trade.executed`'s single `orderId` field** (the taker's, per Step 16's
  documented contract gap) means wallet-service can't always resolve the
  buyer's `FundHold` when the buyer was the maker side of a trade. It falls
  back to a direct debit in that case rather than crashing or guessing —
  verified live this session, see Step 17's journal entry.
- **api-gateway routes only to user-service today** — market-data-service,
  order-service, and wallet-service aren't yet reachable through the gateway
  (each verified directly against its own port).
