package com.chavd.yc01.matchingengine.orderbook;

import com.chavd.yc01.common.dto.event.TradeExecutedEvent;

import java.util.List;

/**
 * Outcome of running one incoming order through OrderBook.match(): zero or
 * more trades, plus whatever quantity didn't fill (0 if it filled completely).
 */
public record MatchResult(
        List<TradeExecutedEvent> trades,
        long remainingQuantity
) {
    public boolean fullyFilled() {
        return remainingQuantity <= 0;
    }
}
