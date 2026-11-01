package com.chavd.yc01.portfolioservice.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Per-symbol unrealized P&L breakdown for one holding. currentPrice is
 * nullable in spirit (see PortfolioReadService's priceKnown handling) but
 * always populated here -- when the live price can't be found, the caller
 * falls back to avgCostBasis so unrealizedPnl comes out as ZERO rather than
 * leaving a hole in the response.
 */
@Builder
public record SymbolPnl(
        String symbol,
        long quantity,
        BigDecimal avgCostBasis,
        BigDecimal currentPrice,
        BigDecimal unrealizedPnl
) {
}
