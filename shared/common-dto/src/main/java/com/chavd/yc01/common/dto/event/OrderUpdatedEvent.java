package com.chavd.yc01.common.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to the {@code order.updated} topic, keyed by orderId, on every
 * order state-machine transition (PENDING -> PLACED -> PARTIAL_FILL -> FILLED / CANCELLED / REJECTED).
 */
@Getter
@Builder
public class OrderUpdatedEvent {

    private final UUID orderId;
    private final OrderStatus status;
    private final long filledQuantity;
    private final Instant timestamp;

    @JsonCreator
    public OrderUpdatedEvent(@JsonProperty("orderId") UUID orderId,
                              @JsonProperty("status") OrderStatus status,
                              @JsonProperty("filledQuantity") long filledQuantity,
                              @JsonProperty("timestamp") Instant timestamp) {
        this.orderId = orderId;
        this.status = status;
        this.filledQuantity = filledQuantity;
        this.timestamp = timestamp;
    }
}
