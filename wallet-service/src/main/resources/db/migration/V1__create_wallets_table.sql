CREATE TABLE wallets (
    id                 UUID PRIMARY KEY,
    user_id            UUID           NOT NULL,
    available_balance  NUMERIC(18, 4) NOT NULL DEFAULT 0,
    held_balance       NUMERIC(18, 4) NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- One wallet per user; also what WalletService's "find or create" upsert
-- (INSERT ... ON CONFLICT (user_id) DO NOTHING) relies on.
CREATE UNIQUE INDEX uq_wallets_user_id ON wallets (user_id);
