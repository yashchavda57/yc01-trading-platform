CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    user_id          UUID           NOT NULL,
    symbol           VARCHAR(16)    NOT NULL,
    side             VARCHAR(4)     NOT NULL,
    order_type       VARCHAR(16)    NOT NULL,
    quantity         BIGINT         NOT NULL,
    price            NUMERIC(18, 4),
    status           VARCHAR(16)    NOT NULL,
    filled_quantity  BIGINT         NOT NULL DEFAULT 0,
    idempotency_key  VARCHAR(128),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id ON orders (user_id);

-- Enforces the idempotency guarantee at the DB level too, not just via the
-- Redis dedup check: two orders can never share a client-supplied key.
CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
