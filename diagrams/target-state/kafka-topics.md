# Target-State Kafka Topic Flow

Every topic from `CLAUDE.md`'s Kafka Topics table, producer → topic →
consumer(s), with the partition key that determines ordering guarantees.

```mermaid
flowchart LR
    MarketData["market-data-service"]
    Order["order-service"]
    Matching["matching-engine"]
    Portfolio["portfolio-service"]
    Wallet["wallet-service"]
    Risk["risk-service"]
    Notification["notification-service"]

    MarketTicks{{"market.ticks<br/>key: symbol"}}
    OrderPlaced{{"order.placed<br/>key: symbol"}}
    TradeExecuted{{"trade.executed<br/>key: user_id"}}
    OrderUpdated{{"order.updated<br/>key: order_id"}}
    NotificationEvents{{"notification.events<br/>key: user_id"}}

    MarketData -- produces --> MarketTicks
    MarketTicks -- consumes --> Risk

    Order -- produces --> OrderPlaced
    OrderPlaced -- consumes --> Matching

    Matching -- produces --> TradeExecuted
    TradeExecuted -- consumes --> Portfolio
    TradeExecuted -- consumes --> Wallet
    TradeExecuted -- consumes --> Notification

    Matching -- produces --> OrderUpdated
    OrderUpdated -- consumes --> Order
    OrderUpdated -- consumes --> Notification

    Order -.produces.-> NotificationEvents
    Portfolio -.produces.-> NotificationEvents
    Wallet -.produces.-> NotificationEvents
    NotificationEvents -- consumes --> Notification
```

## Delivery semantics

- **At-least-once + idempotent consumers**, not exactly-once, is the honest
  framing throughout — see the outbox pattern note below. The one exception
  called out in CLAUDE.md is trade settlement, which uses Kafka transactions
  for exactly-once.
- Every consumer has a **Dead Letter Queue** for unprocessable messages
  (poison pills, schema mismatches).
- `order.placed` is published via the **Outbox pattern** from order-service —
  the DB write and the Kafka publish are never in the same atomic operation,
  so delivery is at-least-once by construction. Consumers (matching-engine)
  must dedup on `orderId`.
