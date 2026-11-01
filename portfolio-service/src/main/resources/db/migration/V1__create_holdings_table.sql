CREATE TABLE holdings (
    id              UUID PRIMARY KEY,
    user_id         UUID           NOT NULL,
    symbol          VARCHAR(16)    NOT NULL,
    quantity        BIGINT         NOT NULL DEFAULT 0,
    avg_cost_basis  NUMERIC(18, 8) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One holding row per (user, symbol); also what PortfolioWriteService's
-- "find-or-create, catch the unique violation on a creation race" pattern
-- relies on, same shape as wallet-service's uq_wallets_user_id.
CREATE UNIQUE INDEX uq_holdings_user_symbol ON holdings (user_id, symbol);
