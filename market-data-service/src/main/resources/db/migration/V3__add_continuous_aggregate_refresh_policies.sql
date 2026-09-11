-- Kept in its own migration: TimescaleDB requires add_continuous_aggregate_policy
-- to run in a separate transaction from the CREATE MATERIALIZED VIEW that defines
-- the aggregate (the view's catalog metadata must commit first).

SELECT add_continuous_aggregate_policy('candles_1m',
    start_offset      => INTERVAL '1 hour',
    end_offset        => INTERVAL '1 minute',
    schedule_interval  => INTERVAL '1 minute');

SELECT add_continuous_aggregate_policy('candles_5m',
    start_offset      => INTERVAL '1 day',
    end_offset        => INTERVAL '5 minutes',
    schedule_interval  => INTERVAL '5 minutes');

SELECT add_continuous_aggregate_policy('candles_1h',
    start_offset      => INTERVAL '7 days',
    end_offset        => INTERVAL '1 hour',
    schedule_interval  => INTERVAL '1 hour');

SELECT add_continuous_aggregate_policy('candles_1d',
    start_offset      => INTERVAL '30 days',
    end_offset        => INTERVAL '1 day',
    schedule_interval  => INTERVAL '1 day');
