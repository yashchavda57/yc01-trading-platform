package com.chavd.yc01.portfolioservice.dto;

import com.chavd.yc01.portfolioservice.entity.Holding;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * The read model served from the Caffeine/Redis cache chain -- deliberately
 * not the Holding entity itself, so the JSON cached in Redis has a stable
 * shape independent of JPA entity fields (id, timestamps) nothing downstream
 * needs. Jackson deserializes records natively (no @JsonCreator needed) when
 * this round-trips back out of Redis.
 */
@Builder
public record HoldingResponse(String symbol, long quantity, BigDecimal avgCostBasis) {

    public static HoldingResponse from(Holding holding) {
        return HoldingResponse.builder()
                .symbol(holding.getSymbol())
                .quantity(holding.getQuantity())
                .avgCostBasis(holding.getAvgCostBasis())
                .build();
    }
}
