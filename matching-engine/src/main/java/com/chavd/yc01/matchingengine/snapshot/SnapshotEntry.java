package com.chavd.yc01.matchingengine.snapshot;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

/** One resting order's worth of state, the unit that gets JSON-serialized into a snapshot row. */
public record SnapshotEntry(
        @JsonProperty("orderId") UUID orderId,
        @JsonProperty("userId") UUID userId,
        @JsonProperty("side") OrderSide side,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("quantity") long quantity
) {
    @JsonCreator
    public SnapshotEntry {
    }
}
