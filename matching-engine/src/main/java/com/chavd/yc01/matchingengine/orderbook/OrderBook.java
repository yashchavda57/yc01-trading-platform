package com.chavd.yc01.matchingengine.orderbook;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderType;
import com.chavd.yc01.common.dto.event.TradeExecutedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * One instrument's order book. Bids sorted descending (highest price = best
 * bid, matched first), asks sorted ascending (lowest price = best ask,
 * matched first) -- ConcurrentSkipListMap gives O(log n) insert/remove/first
 * entry with a thread-safe sorted structure, so no separate sort step is ever
 * needed.
 * <p>
 * One ReentrantReadWriteLock per instrument (not one global lock): AAPL orders
 * never contend with TSLA orders. Matching takes the write lock (mutates the
 * book); depth/inspection reads would take the read lock. Correctness first --
 * see MatchingEngineBenchmark for whether/where this is worth optimizing
 * further, decided by measurement rather than assumption.
 */
public class OrderBook {

    private final String symbol;
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<BigDecimal, PriceLevel> asks =
            new ConcurrentSkipListMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }

    public MatchResult match(IncomingOrder incoming) {
        lock.writeLock().lock();
        try {
            List<TradeExecutedEvent> trades = new ArrayList<>();
            ConcurrentSkipListMap<BigDecimal, PriceLevel> opposite =
                    incoming.side() == OrderSide.BUY ? asks : bids;

            long remaining = incoming.quantity();

            while (remaining > 0) {
                Map.Entry<BigDecimal, PriceLevel> best = opposite.firstEntry();
                if (best == null || !pricesCross(incoming, best.getKey())) {
                    break;
                }

                PriceLevel level = best.getValue();
                RestingOrder resting = level.peek();
                if (resting == null) {
                    opposite.remove(best.getKey());
                    continue;
                }

                long fillQty = Math.min(remaining, resting.getRemainingQuantity());
                trades.add(buildTrade(incoming, resting, best.getKey(), fillQty));

                remaining -= fillQty;
                resting.fill(fillQty);

                if (resting.isFullyFilled()) {
                    level.removeHead();
                }
                if (level.isEmpty()) {
                    opposite.remove(best.getKey());
                }
            }

            if (remaining > 0 && incoming.orderType() != OrderType.MARKET) {
                RestingOrder toRest = new RestingOrder(
                        incoming.orderId(), incoming.userId(), incoming.symbol(),
                        incoming.side(), incoming.price(), remaining);
                ownBook(incoming.side()).computeIfAbsent(incoming.price(), p -> new PriceLevel())
                        .add(toRest);
            }
            // A MARKET order with leftover quantity and no more liquidity on the
            // opposite side is simply left unfilled (never rested) -- a real
            // exchange would reject/cancel the remainder explicitly; that
            // lifecycle event isn't modeled here yet, worth naming as a gap.

            return new MatchResult(trades, Math.max(remaining, 0));
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ConcurrentSkipListMap<BigDecimal, PriceLevel> ownBook(OrderSide side) {
        return side == OrderSide.BUY ? bids : asks;
    }

    private boolean pricesCross(IncomingOrder incoming, BigDecimal bestOppositePrice) {
        if (incoming.orderType() == OrderType.MARKET) {
            return true;
        }
        return incoming.side() == OrderSide.BUY
                ? incoming.price().compareTo(bestOppositePrice) >= 0
                : incoming.price().compareTo(bestOppositePrice) <= 0;
    }

    private TradeExecutedEvent buildTrade(IncomingOrder incoming, RestingOrder resting,
                                           BigDecimal tradePrice, long quantity) {
        UUID buyOrderId = incoming.side() == OrderSide.BUY ? incoming.orderId() : resting.getOrderId();
        UUID buyUserId = incoming.side() == OrderSide.BUY ? incoming.userId() : resting.getUserId();
        UUID sellUserId = incoming.side() == OrderSide.BUY ? resting.getUserId() : incoming.userId();

        // TradeExecutedEvent (shared/common-dto, Step 13) carries a single
        // "orderId", not separate buy/sell order ids -- set to the taker's
        // (incoming) order id here. A real trade record needs both; flagged as
        // a known contract limitation in the Step 16 journal entry.
        return TradeExecutedEvent.builder()
                .tradeId(UUID.randomUUID())
                .orderId(incoming.orderId())
                .buyUserId(buyUserId)
                .sellUserId(sellUserId)
                .symbol(symbol)
                .quantity(quantity)
                .price(tradePrice)
                .timestamp(Instant.now())
                .build();
    }

    /** For tests/snapshot: best bid, or null if the book is empty on that side. */
    public BigDecimal bestBid() {
        Map.Entry<BigDecimal, PriceLevel> entry = bids.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    public BigDecimal bestAsk() {
        Map.Entry<BigDecimal, PriceLevel> entry = asks.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    public int restingCountAtPrice(OrderSide side, BigDecimal price) {
        PriceLevel level = ownBook(side).get(price);
        return level == null ? 0 : level.size();
    }

    /** All currently-resting orders, bids then asks -- used only for snapshotting. */
    public List<RestingOrder> allResting() {
        lock.readLock().lock();
        try {
            List<RestingOrder> all = new ArrayList<>();
            collectInto(bids, all);
            collectInto(asks, all);
            return all;
        } finally {
            lock.readLock().unlock();
        }
    }

    private void collectInto(ConcurrentSkipListMap<BigDecimal, PriceLevel> side, List<RestingOrder> out) {
        for (PriceLevel level : side.values()) {
            out.addAll(level.snapshot());
        }
    }

    /**
     * Places an order directly onto the book without running it through
     * matching -- used only to rebuild state from a snapshot on startup, where
     * the order was already resting (and already excluded from re-matching
     * against whatever else is in the same snapshot).
     */
    public void restore(RestingOrder order) {
        lock.writeLock().lock();
        try {
            ownBook(order.getSide()).computeIfAbsent(order.getPrice(), p -> new PriceLevel()).add(order);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
