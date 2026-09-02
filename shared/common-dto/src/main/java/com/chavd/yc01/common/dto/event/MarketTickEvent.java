package com.chavd.yc01.common.dto.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published to the {@code market.ticks} topic, keyed by symbol.
 */
@Getter
@Builder
public class MarketTickEvent {

    private final String symbol;
    private final BigDecimal price;
    private final long volume;
    private final Instant timestamp;

    @JsonCreator
    public MarketTickEvent(@JsonProperty("symbol") String symbol,
                            @JsonProperty("price") BigDecimal price,
                            @JsonProperty("volume") long volume,
                            @JsonProperty("timestamp") Instant timestamp) {
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.timestamp = timestamp;
    }
}
