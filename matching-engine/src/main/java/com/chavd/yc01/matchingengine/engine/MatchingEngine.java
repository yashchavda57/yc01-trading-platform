package com.chavd.yc01.matchingengine.engine;

import com.chavd.yc01.matchingengine.orderbook.IncomingOrder;
import com.chavd.yc01.matchingengine.orderbook.MatchResult;
import com.chavd.yc01.matchingengine.orderbook.OrderBook;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes each incoming order to its instrument's OrderBook, creating one on
 * first sight of a symbol. One book per symbol is the whole concurrency
 * strategy: AAPL and TSLA orders never contend for the same lock because
 * they're never in the same OrderBook.
 */
public class MatchingEngine {

    private final Map<String, OrderBook> booksBySymbol = new ConcurrentHashMap<>();

    public MatchResult process(IncomingOrder order) {
        OrderBook book = booksBySymbol.computeIfAbsent(order.symbol(), OrderBook::new);
        return book.match(order);
    }

    public OrderBook bookFor(String symbol) {
        return booksBySymbol.get(symbol);
    }

    public Map<String, OrderBook> allBooks() {
        return booksBySymbol;
    }
}
