package com.chavd.yc01.common.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to the {@code trade.executed} topic, keyed by userId, whenever the
 * matching engine fills (fully or partially) a resting order against an incoming one.
 */
@Getter
@Builder
public class TradeExecutedEvent {

    private final UUID tradeId;
    private final UUID orderId;
    private final UUID buyUserId;
    private final UUID sellUserId;
    private final String symbol;
    private final long quantity;
    private final BigDecimal price;
    private final Instant timestamp;

    @JsonCreator
    public TradeExecutedEvent(@JsonProperty("tradeId") UUID tradeId,
                               @JsonProperty("orderId") UUID orderId,
                               @JsonProperty("buyUserId") UUID buyUserId,
                               @JsonProperty("sellUserId") UUID sellUserId,
                               @JsonProperty("symbol") String symbol,
                               @JsonProperty("quantity") long quantity,
                               @JsonProperty("price") BigDecimal price,
                               @JsonProperty("timestamp") Instant timestamp) {
        this.tradeId = tradeId;
        this.orderId = orderId;
        this.buyUserId = buyUserId;
        this.sellUserId = sellUserId;
        this.symbol = symbol;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }
}
