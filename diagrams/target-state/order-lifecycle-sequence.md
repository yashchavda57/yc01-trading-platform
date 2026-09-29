# Target-State Order Lifecycle (Sequence)

The full request-to-settlement path for one order, end to end, once every
service in `CLAUDE.md` exists.

```mermaid
sequenceDiagram
    actor U as User
    participant GW as api-gateway
    participant OS as order-service
    participant DB as Postgres (orders + outbox)
    participant K as Kafka
    participant ME as matching-engine
    participant PF as portfolio-service
    participant WS as wallet-service
    participant NS as notification-service
    participant RS as risk-service

    U->>GW: POST /orders (Idempotency-Key)
    GW->>RS: pre-trade risk check
    RS-->>GW: OK (within limits)
    GW->>OS: place order
    OS->>DB: INSERT order + outbox_event (1 tx)
    OS-->>U: 201 PLACED

    Note over OS,K: OutboxPollerService (separate scheduled process)
    OS->>K: order.placed (key=symbol)

    K->>ME: order.placed
    ME->>ME: OrderBook.match() price-time priority
    ME->>K: trade.executed (key=user_id)
    ME->>K: order.updated (key=order_id)

    par settle
        K->>PF: trade.executed -> update holdings, P&L
        K->>WS: trade.executed -> debit/credit ledger (double-entry)
    and notify
        K->>NS: trade.executed -> push notification
    and reconcile
        K->>OS: order.updated -> reflect FILLED/PARTIAL_FILL
        K->>NS: order.updated -> notify user
    end
```

## Notes

- The risk check on placement is a **synchronous pre-trade check**
  (position limits, margin) — everything after the order is placed is
  **asynchronous, choreographed via Kafka** (a Saga, not an orchestrator).
- Wallet fund freeze on placement / release on cancel-or-expiry isn't drawn
  here to keep the diagram readable — it hangs off the same `order.placed` /
  `order.updated` events, consumed by wallet-service.
- See `../current-state/order-lifecycle-sequence.md` for how much of this
  path is actually wired up and verified today.
