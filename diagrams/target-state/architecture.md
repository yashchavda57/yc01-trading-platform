# Target-State Architecture

The full system as scoped in `CLAUDE.md`, all 5 phases complete. This is the
end goal, not the current state — see `../current-state/architecture.md` for
what's actually built.

```mermaid
flowchart TB
    Client(["Client"])

    subgraph Edge["Edge"]
        Gateway["api-gateway<br/>(Spring Cloud Gateway)<br/>JWT · rate limit · circuit breaker"]
    end

    subgraph Discovery["Platform"]
        Eureka["service-discovery<br/>(Eureka)"]
        Config["config-server"]
    end

    subgraph Services["Core Services"]
        User["user-service<br/>Auth · KYC · RBAC"]
        MarketData["market-data-service<br/>Ticks · candles · WebSocket"]
        Order["order-service<br/>Order state machine · outbox"]
        Matching["matching-engine<br/>Order book · price-time priority"]
        Portfolio["portfolio-service<br/>CQRS · P&L"]
        Wallet["wallet-service<br/>Double-entry ledger"]
        Risk["risk-service<br/>Kafka Streams · pre-trade checks"]
        Notification["notification-service<br/>Email/SMS/Push · DLQ"]
        Report["report-service<br/>Spring Batch · Elasticsearch"]
    end

    Kafka{{"Kafka Cluster"}}

    subgraph Stores["Data Stores"]
        Postgres[("PostgreSQL<br/>users · orders · wallet · positions")]
        Timescale[("TimescaleDB<br/>ticks · candles")]
        Redis[("Redis<br/>cache · sessions · rate limit")]
        ES[("Elasticsearch<br/>trade history · audit")]
    end

    subgraph Observability["Observability"]
        Prometheus["Prometheus"]
        Grafana["Grafana"]
        ELK["ELK Stack"]
        Zipkin["Zipkin"]
    end

    Client --> Gateway
    Gateway --> User
    Gateway --> MarketData
    Gateway --> Order
    Gateway --> Portfolio
    Gateway --> Wallet
    Gateway --> Report

    Services -.registers with.-> Eureka
    Services -.pulls config from.-> Config

    User --> Postgres
    Order --> Postgres
    Wallet --> Postgres
    Portfolio --> Redis
    MarketData --> Timescale
    MarketData --> Redis
    Report --> ES

    MarketData -- market.ticks --> Kafka
    Order -- order.placed --> Kafka
    Kafka -- order.placed --> Matching
    Matching -- trade.executed --> Kafka
    Matching -- order.updated --> Kafka
    Kafka -- trade.executed --> Portfolio
    Kafka -- trade.executed --> Wallet
    Kafka -- trade.executed --> Notification
    Kafka -- order.updated --> Order
    Kafka -- order.updated --> Notification
    Kafka -- market.ticks --> Risk
    Kafka -- notification.events --> Notification

    Services -.metrics.-> Prometheus --> Grafana
    Services -.logs.-> ELK
    Services -.traces.-> Zipkin
```

## Key patterns this diagram implies

| Pattern | Where |
|---|---|
| Saga (choreography) | Order placement → fund freeze (wallet) → match (matching-engine) → settle (portfolio/wallet), all via Kafka events, no direct service-to-service calls in the happy path |
| Outbox | order-service → Kafka, guaranteed delivery |
| CQRS | portfolio-service: write side event-driven, read side materialized in Redis |
| Circuit breaker / bulkhead | Gateway and order-service, via Resilience4j |
| Dead letter queue | Every Kafka consumer service |

See `../target-state/kafka-topics.md` for the topic-level detail behind the
Kafka arrows above, and `../target-state/order-lifecycle-sequence.md` for the
full request-to-settlement flow through this diagram.
