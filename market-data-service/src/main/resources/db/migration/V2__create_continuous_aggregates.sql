-- Each of these is a materialized view that TimescaleDB incrementally refreshes
-- as new ticks land, instead of recomputing OHLCV aggregation from raw ticks on
-- every read. "first(price, ts)" / "last(price, ts)" give the open/close price
-- ordered by time within each bucket, independent of insertion order.

CREATE MATERIALIZED VIEW candles_1m
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 minute', ts) AS bucket,
    first(price, ts)            AS open,
    max(price)                  AS high,
    min(price)                  AS low,
    last(price, ts)             AS close,
    sum(volume)                 AS volume
FROM ticks
GROUP BY symbol, bucket
WITH NO DATA;

CREATE MATERIALIZED VIEW candles_5m
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('5 minutes', ts) AS bucket,
    first(price, ts)             AS open,
    max(price)                   AS high,
    min(price)                   AS low,
    last(price, ts)              AS close,
    sum(volume)                  AS volume
FROM ticks
GROUP BY symbol, bucket
WITH NO DATA;

CREATE MATERIALIZED VIEW candles_1h
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 hour', ts) AS bucket,
    first(price, ts)          AS open,
    max(price)                AS high,
    min(price)                AS low,
    last(price, ts)           AS close,
    sum(volume)                AS volume
FROM ticks
GROUP BY symbol, bucket
WITH NO DATA;

CREATE MATERIALIZED VIEW candles_1d
WITH (timescaledb.continuous) AS
SELECT
    symbol,
    time_bucket('1 day', ts) AS bucket,
    first(price, ts)         AS open,
    max(price)               AS high,
    min(price)               AS low,
    last(price, ts)          AS close,
    sum(volume)               AS volume
FROM ticks
GROUP BY symbol, bucket
WITH NO DATA;
