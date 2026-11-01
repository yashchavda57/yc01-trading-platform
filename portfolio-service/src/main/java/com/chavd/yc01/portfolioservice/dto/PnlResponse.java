package com.chavd.yc01.portfolioservice.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * realizedPnl is an exact sum of booked RealizedPnlEntry facts (Postgres,
 * source of truth). unrealizedPnl is a point-in-time estimate against
 * whatever's currently in market-data-service's Redis price cache -- it can
 * legitimately change between two calls a second apart with no trade having
 * happened, which is expected and worth calling out as the difference
 * between the two numbers in an interview.
 */
@Builder
public record PnlResponse(
        UUID userId,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalPnl,
        List<SymbolPnl> holdings
) {
}
