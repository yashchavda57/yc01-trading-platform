CREATE TABLE realized_pnl_entries (
    id                     UUID PRIMARY KEY,
    user_id                UUID           NOT NULL,
    symbol                 VARCHAR(16)    NOT NULL,
    trade_id               UUID           NOT NULL,
    quantity               BIGINT         NOT NULL,
    sell_price             NUMERIC(18, 8) NOT NULL,
    avg_cost_basis_at_sale NUMERIC(18, 8) NOT NULL,
    realized_pnl           NUMERIC(18, 8) NOT NULL,
    created_at             TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- The idempotency guard for Kafka redelivery: the same trade.executed message
-- processed twice must not book the same trade's realized P&L twice. Same
-- shape as wallet-service's uq_ledger_entries_reference.
CREATE UNIQUE INDEX uq_realized_pnl_entries_trade_id ON realized_pnl_entries (trade_id);
CREATE INDEX idx_realized_pnl_entries_user_id ON realized_pnl_entries (user_id);
