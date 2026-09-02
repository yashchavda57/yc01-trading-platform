package com.chavd.yc01.common.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to the {@code order.placed} topic, keyed by symbol, once order-service
 * has durably recorded the order (via the outbox pattern).
 */
@Getter
@Builder
public class OrderPlacedEvent {

    private final UUID orderId;
    private final UUID userId;
    private final String symbol;
    private final OrderSide side;
    private final OrderType orderType;
    private final long quantity;
    private final BigDecimal price;
    private final Instant timestamp;

    @JsonCreator
    public OrderPlacedEvent(@JsonProperty("orderId") UUID orderId,
                             @JsonProperty("userId") UUID userId,
                             @JsonProperty("symbol") String symbol,
                             @JsonProperty("side") OrderSide side,
                             @JsonProperty("orderType") OrderType orderType,
                             @JsonProperty("quantity") long quantity,
                             @JsonProperty("price") BigDecimal price,
                             @JsonProperty("timestamp") Instant timestamp) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }
}
