package com.chavd.yc01.matchingengine.orderbook;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * All resting orders at one exact price, in strict arrival order.
 * ConcurrentLinkedQueue is a FIFO by construction — the head is always the
 * order that's been waiting longest at this price, which is exactly
 * time-priority within a price level.
 */
public class PriceLevel {

    private final Queue<RestingOrder> orders = new ConcurrentLinkedQueue<>();

    public void add(RestingOrder order) {
        orders.add(order);
    }

    public RestingOrder peek() {
        return orders.peek();
    }

    public void removeHead() {
        orders.poll();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public int size() {
        return orders.size();
    }

    /** Weakly-consistent point-in-time copy of every order at this price, for snapshotting. */
    public List<RestingOrder> snapshot() {
        return List.copyOf(orders);
    }
}
