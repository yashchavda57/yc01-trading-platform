CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    wallet_id       UUID           NOT NULL REFERENCES wallets (id),
    entry_type      VARCHAR(8)     NOT NULL, -- DEBIT or CREDIT
    amount          NUMERIC(18, 4) NOT NULL, -- always positive; entry_type gives direction
    reference_type  VARCHAR(32)    NOT NULL, -- e.g. TRADE, DEPOSIT
    reference_id    UUID           NOT NULL, -- e.g. tradeId
    description     VARCHAR(256),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- The idempotency guard for Kafka redelivery: the same trade.executed message
-- processed twice must not post the same wallet's debit/credit twice for the
-- same reference. This is the DB-level backstop, same shape as order-service's
-- idempotency_key unique index in Step 15.
CREATE UNIQUE INDEX uq_ledger_entries_reference ON ledger_entries (reference_id, wallet_id, entry_type);
CREATE INDEX idx_ledger_entries_wallet_id ON ledger_entries (wallet_id);
