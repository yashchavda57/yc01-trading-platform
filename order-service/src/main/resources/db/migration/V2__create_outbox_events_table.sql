-- Outbox pattern: a row here is inserted in the SAME @Transactional write as
-- the "orders" row it describes, so both commit or both roll back together.
-- A separate poller (OutboxPollerService) is the only thing that ever
-- publishes to Kafka, reading unpublished rows on a schedule.
CREATE TABLE outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(32)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    topic          VARCHAR(64)  NOT NULL,
    -- Kafka message key. order.placed is partitioned by symbol (see CLAUDE.md's
    -- topic table), NOT by order id, so every order for the same instrument
    -- lands on the same partition and matching-engine sees them in order.
    partition_key  VARCHAR(64)  NOT NULL,
    payload        TEXT         NOT NULL,
    published      BOOLEAN      NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at   TIMESTAMPTZ
);

-- The poller's query is "give me unpublished rows, oldest first" - this index
-- makes that a cheap index scan instead of a full table scan as the table grows.
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at)
    WHERE published = false;
