CREATE TABLE fund_holds (
    id          UUID PRIMARY KEY,
    wallet_id   UUID           NOT NULL REFERENCES wallets (id),
    order_id    UUID           NOT NULL,
    amount      NUMERIC(18, 4) NOT NULL,
    status      VARCHAR(16)    NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One hold per order: a redelivered order.placed (at-least-once, same theme
-- as Step 15's outbox) must not double-freeze funds for the same order.
CREATE UNIQUE INDEX uq_fund_holds_order_id ON fund_holds (order_id);
CREATE INDEX idx_fund_holds_wallet_id ON fund_holds (wallet_id);
