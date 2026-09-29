package com.chavd.yc01.matchingengine.orderbook;

import com.chavd.yc01.common.dto.event.OrderSide;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An order actually sitting in the book, waiting to be matched. Mutable
 * remaining quantity because a partial fill shrinks it in place rather than
 * replacing the object — the PriceLevel queue holds this same instance for
 * its whole life on the book, which is what keeps time-priority (FIFO) intact:
 * a partially-filled order does NOT move to the back of the queue.
 */
@Getter
public class RestingOrder {

    private final UUID orderId;
    private final UUID userId;
    private final String symbol;
    private final OrderSide side;
    private final BigDecimal price;
    private final long enteredAtNanos;

    private final AtomicLong remainingQuantity;

    public RestingOrder(UUID orderId, UUID userId, String symbol, OrderSide side,
                         BigDecimal price, long quantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.remainingQuantity = new AtomicLong(quantity);
        this.enteredAtNanos = System.nanoTime();
    }

    public long getRemainingQuantity() {
        return remainingQuantity.get();
    }

    /** Returns the new remaining quantity after the fill. */
    public long fill(long quantity) {
        return remainingQuantity.addAndGet(-quantity);
    }

    public boolean isFullyFilled() {
        return remainingQuantity.get() <= 0;
    }
}
