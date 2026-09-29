package com.chavd.yc01.matchingengine.orderbook;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderType;
import com.chavd.yc01.common.dto.event.TradeExecutedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static final String SYMBOL = "AAPL";

    private IncomingOrder limitOrder(OrderSide side, String price, long qty) {
        return new IncomingOrder(UUID.randomUUID(), UUID.randomUUID(), SYMBOL, side,
                OrderType.LIMIT, new BigDecimal(price), qty);
    }

    private IncomingOrder marketOrder(OrderSide side, long qty) {
        return new IncomingOrder(UUID.randomUUID(), UUID.randomUUID(), SYMBOL, side,
                OrderType.MARKET, null, qty);
    }

    @Test
    void restingOrderWithNoCrossingPrice_justRests() {
        OrderBook book = new OrderBook(SYMBOL);

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "100.00", 10));

        assertTrue(result.trades().isEmpty());
        assertEquals(10, result.remainingQuantity());
        assertEquals(new BigDecimal("100.00"), book.bestBid());
        assertNull(book.bestAsk());
    }

    @Test
    void exactPriceMatch_fullyFillsBothSides() {
        OrderBook book = new OrderBook(SYMBOL);
        book.match(limitOrder(OrderSide.SELL, "100.00", 10)); // resting ask

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "100.00", 10));

        assertEquals(1, result.trades().size());
        TradeExecutedEvent trade = result.trades().get(0);
        assertEquals(new BigDecimal("100.00"), trade.getPrice());
        assertEquals(10, trade.getQuantity());
        assertTrue(result.fullyFilled());
        assertNull(book.bestAsk(), "fully filled resting order should be removed from the book");
        assertNull(book.bestBid(), "fully filled incoming order should not rest");
    }

    @Test
    void partialFill_restsTheRemainder() {
        OrderBook book = new OrderBook(SYMBOL);
        book.match(limitOrder(OrderSide.SELL, "100.00", 4)); // smaller resting ask

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "100.00", 10));

        assertEquals(1, result.trades().size());
        assertEquals(4, result.trades().get(0).getQuantity());
        assertEquals(6, result.remainingQuantity());
        assertNull(book.bestAsk());
        assertEquals(new BigDecimal("100.00"), book.bestBid());
        assertEquals(1, book.restingCountAtPrice(OrderSide.BUY, new BigDecimal("100.00")));
    }

    @Test
    void fifoWithinSamePriceLevel_firstRestingOrderFillsFirst() {
        OrderBook book = new OrderBook(SYMBOL);
        // Distinguishable quantities so we can tell WHICH resting order absorbed the fill.
        book.match(limitOrder(OrderSide.SELL, "100.00", 3));  // arrives first
        book.match(limitOrder(OrderSide.SELL, "100.00", 5));  // arrives second

        // Exactly enough to fully consume the first resting order and nothing else.
        MatchResult result = book.match(limitOrder(OrderSide.BUY, "100.00", 3));

        assertEquals(1, result.trades().size());
        assertEquals(3, result.trades().get(0).getQuantity());
        assertTrue(result.fullyFilled());
        // Only one order left at this price level -- the first (3-qty) one was fully
        // consumed and removed; if time priority were broken, the second (5-qty) one
        // would have been touched instead and this would still show 1, but the
        // NEXT match below proves which one actually remains.
        assertEquals(1, book.restingCountAtPrice(OrderSide.SELL, new BigDecimal("100.00")));

        // The remaining resting order must be the second one (still 5 unfilled) --
        // matching exactly 5 more should fully clear the book.
        MatchResult second = book.match(limitOrder(OrderSide.BUY, "100.00", 5));
        assertEquals(1, second.trades().size());
        assertEquals(5, second.trades().get(0).getQuantity());
        assertNull(book.bestAsk());
    }

    @Test
    void noMatch_whenPricesDoNotCross() {
        OrderBook book = new OrderBook(SYMBOL);
        book.match(limitOrder(OrderSide.SELL, "101.00", 10));

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "100.00", 10));

        assertTrue(result.trades().isEmpty());
        assertEquals(10, result.remainingQuantity());
        assertEquals(new BigDecimal("101.00"), book.bestAsk());
        assertEquals(new BigDecimal("100.00"), book.bestBid());
    }

    @Test
    void marketOrder_matchesAgainstBestAvailablePriceRegardlessOfLimit() {
        OrderBook book = new OrderBook(SYMBOL);
        book.match(limitOrder(OrderSide.SELL, "105.00", 10));

        MatchResult result = book.match(marketOrder(OrderSide.BUY, 10));

        assertEquals(1, result.trades().size());
        assertEquals(new BigDecimal("105.00"), result.trades().get(0).getPrice());
        assertTrue(result.fullyFilled());
    }

    @Test
    void marketOrder_withNoLiquidity_isNotRested() {
        OrderBook book = new OrderBook(SYMBOL);

        MatchResult result = book.match(marketOrder(OrderSide.BUY, 10));

        assertTrue(result.trades().isEmpty());
        assertEquals(10, result.remainingQuantity());
        assertNull(book.bestBid(), "MARKET orders must never rest on the book");
    }

    @Test
    void walksMultiplePriceLevels_untilIncomingOrderIsFilled() {
        OrderBook book = new OrderBook(SYMBOL);
        book.match(limitOrder(OrderSide.SELL, "100.00", 5));
        book.match(limitOrder(OrderSide.SELL, "101.00", 5));

        MatchResult result = book.match(limitOrder(OrderSide.BUY, "101.00", 10));

        assertEquals(2, result.trades().size());
        assertEquals(new BigDecimal("100.00"), result.trades().get(0).getPrice());
        assertEquals(new BigDecimal("101.00"), result.trades().get(1).getPrice());
        assertTrue(result.fullyFilled());
    }
}
