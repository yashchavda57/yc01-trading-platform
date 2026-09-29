package com.chavd.yc01.matchingengine.orderbook;

import com.chavd.yc01.common.dto.event.OrderSide;
import com.chavd.yc01.common.dto.event.OrderType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * An order.placed event, translated into what the matching loop needs. Kept
 * separate from RestingOrder because an incoming order that fully fills never
 * needs a RestingOrder created for it at all.
 */
public record IncomingOrder(
        UUID orderId,
        UUID userId,
        String symbol,
        OrderSide side,
        OrderType orderType,
        BigDecimal price,
        long quantity
) {
}
