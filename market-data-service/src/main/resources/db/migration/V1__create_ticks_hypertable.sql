CREATE EXTENSION IF NOT EXISTS timescaledb;

CREATE TABLE ticks (
    symbol VARCHAR(16)     NOT NULL,
    price  NUMERIC(18, 4)  NOT NULL,
    volume BIGINT          NOT NULL,
    ts     TIMESTAMPTZ     NOT NULL
);

-- Turns the plain table above into a hypertable: TimescaleDB transparently
-- partitions rows into per-time-range "chunks" on the ts column, while every
-- query still just sees one logical "ticks" table.
SELECT create_hypertable('ticks', 'ts');

CREATE INDEX idx_ticks_symbol_ts ON ticks (symbol, ts DESC);
